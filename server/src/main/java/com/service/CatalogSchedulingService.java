package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.model.*;
import com.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Reuses the catalog for both legacy school-wide runs and department draft plans. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogSchedulingService {
    private final LecturerAccountService lecturerAccounts;
    private final CatalogService catalogs;
    private final InstitutionLecturerRepository lecturers;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final AutoScheduleService automatic;
    private final ScheduleWriteLock writeLock;
    private final ObjectMapper json;

    private record Context(Options options, Map<String, Teacher> teachers) {}
    private record CourseKey(String code, String cohort) {
        CourseKey { code = code.strip().toUpperCase(Locale.ROOT); cohort = cohort.strip().toUpperCase(Locale.ROOT); }
    }

    public Options options(String schoolCode) throws Exception { return context(schoolCode).options(); }

    public Options optionsForDepartment(String schoolCode, Long departmentId) throws Exception {
        return departmentOptions(context(schoolCode).options(), departmentId);
    }

    public record DraftProposal(SchoolWideScheduleRequest request, AutoScheduleResponse result) {}
    private record Prepared(SchoolWideScheduleRequest request, List<Room> rooms, RunContext source) {}

    @Transactional(readOnly = true)
    public DraftProposal draftProposal(String schoolCode, Long departmentId, Long planId, LocalDate start,
                                       MultipartFile planFile, Configuration config) throws Exception {
        ScheduleValidation.monday(start);
        if (config == null || config.replaceSchoolSchedule()) throw new IllegalArgumentException("Bản lịch khoa không dùng chế độ thay toàn trường.");
        var prepared = prepare(schoolCode, start, new WeeklyPlanExcelReader().readPlan(planFile), config, departmentId, planId, false);
        var result = automatic.previewScheduleWithRooms(prepared.request(), prepared.rooms());
        result.setCatalogContext(prepared.source());
        return new DraftProposal(prepared.request(), result);
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse generate(String schoolCode, LocalDate semesterStartDate, MultipartFile planFile, Configuration config) throws Exception {
        ScheduleValidation.monday(semesterStartDate);
        if (config == null || config.snapshot() == null || !config.snapshot().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Cần tải danh mục trước khi xếp lịch.");
        if (!config.replaceSchoolSchedule()) throw new IllegalArgumentException("Cần xác nhận kế hoạch đầy đủ cho toàn trường trước khi cập nhật lịch tự động.");
        var courses = new WeeklyPlanExcelReader().readPlan(planFile);
        writeLock.acquire();
        automatic.requireSchoolWideWriteAllowed(CatalogService.schoolCode(schoolCode));
        var prepared = prepare(schoolCode, semesterStartDate, courses, config, null, null, true);
        var result = automatic.generateScheduleWithRooms(prepared.request(), prepared.rooms());
        result.setCatalogContext(prepared.source());
        if (!result.isSaved()) org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return result;
    }

    private Prepared prepare(String schoolCode, LocalDate semesterStartDate, List<CourseDemandDTO> courses,
                             Configuration config, Long departmentId, Long planId, boolean persistIdentity) throws Exception {
        if (config == null || config.snapshot() == null || !config.snapshot().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Cần tải danh mục trước khi xếp lịch.");
        var context = context(schoolCode);
        var options = departmentId == null ? context.options() : departmentOptions(context.options(), departmentId);
        if (!options.snapshot().equals(config.snapshot())) throw new ObjectOptimisticLockingFailureException("CatalogScheduling", schoolCode);
        var selectedLecturers = ids(config.lecturerIds(), "giảng viên");
        var selectedRooms = ids(config.roomIds(), "phòng");
        var profiles = new LinkedHashMap<Long, LecturerView>();
        options.lecturers().stream().filter(LecturerOption::eligible).forEach(o -> profiles.put(o.lecturer().id(), o.lecturer()));
        if (!profiles.keySet().containsAll(selectedLecturers)) throw new IllegalArgumentException("Có giảng viên ngoài trường hoặc không còn đủ điều kiện sử dụng.");
        var roomIds = new HashSet<Long>(); options.rooms().forEach(r -> roomIds.add(r.id()));
        if (!roomIds.containsAll(selectedRooms)) throw new IllegalArgumentException("Có phòng ngoài trường hoặc đã ngừng sử dụng.");
        var periodChoice = options.periodChoices().stream().filter(p -> p.id().equals(config.periodChoiceId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chọn bảng giờ tiết từ danh mục vừa tải."));
        var overrides = overrides(config.weights(), options.assignments(), selectedLecturers);
        var candidates = new LinkedHashMap<CourseKey, List<TeacherCandidateDTO>>();
        for (var assignment : options.assignments()) {
            if (!selectedLecturers.contains(assignment.lecturerId())) continue;
            var profile = profiles.get(assignment.lecturerId());
            if (profile == null) continue;
            var override = overrides.get(assignment.id());
            int theory = override == null ? assignment.theoryWeight() : override.theoryWeight();
            int practice = override == null ? assignment.practiceWeight() : override.practiceWeight();
            if (theory + practice == 0) continue;
            var candidate = TeacherCandidateDTO.builder().catalogLecturerId(profile.id()).lecturerCode(profile.lecturerCode())
                    .teacherId("PENDING_" + profile.id()).teacherName(profile.fullName()).theoryWeight(theory).practiceWeight(practice).build();
            candidates.computeIfAbsent(new CourseKey(assignment.courseCode(), assignment.cohort()), ignored -> new ArrayList<>()).add(candidate);
        }
        var used = new LinkedHashSet<Long>();
        var missing = new ArrayList<String>();
        for (var course : courses) {
            var eligible = candidates.getOrDefault(new CourseKey(course.getCourseCode(), course.getCohort()), List.of());
            String label = "Dòng " + course.getStt() + " (" + course.getCourseCode() + "/" + course.getCohort() + ")";
            if (eligible.isEmpty()) missing.add(label + ": chưa có phân công đang hoạt động trong tập GV đã chọn");
            else {
                if (course.getTheoryPeriods() > 0 && eligible.stream().noneMatch(t -> t.getTheoryWeight() > 0)) missing.add(label + ": thiếu GV có trọng số LT lớn hơn 0");
                if (course.getPracticePeriods() > 0 && eligible.stream().noneMatch(t -> t.getPracticeWeight() > 0)) missing.add(label + ": thiếu GV có trọng số TH lớn hơn 0");
            }
            course.setTeachers(eligible);
            eligible.forEach(t -> used.add(t.getCatalogLecturerId()));
        }
        if (!missing.isEmpty()) throw new IllegalArgumentException(String.join("; ", missing));
        var identities = persistIdentity ? identities(options.school().schoolCode(), used, semesterStartDate) : previewIdentities(options, used, context.teachers());
        var byLecturer = new HashMap<Long, String>(); identities.forEach(i -> byLecturer.put(i.lecturerId(), i.teacherId()));
        courses.forEach(c -> c.getTeachers().forEach(t -> t.setTeacherId(byLecturer.get(t.getCatalogLecturerId()))));
        var availableRooms = rooms.findBySchoolCodeOrderByCapacityAsc(options.school().schoolCode()).stream()
                .filter(r -> r.isActive() && selectedRooms.contains(r.getId())).toList();
        var request = SchoolWideScheduleRequest.builder().semesterStartDate(semesterStartDate).schedulingPlanId(planId).previewOnly(!persistIdentity)
                .totalWeeks((int) ChronoUnit.DAYS.between(semesterStartDate, courses.stream().map(CourseDemandDTO::getStudyEndDate).max(LocalDate::compareTo).orElseThrow()) / 7 + 1)
                .targetSchool(SchoolConfigDTO.builder().schoolId(options.school().schoolCode()).schoolName(options.school().name())
                        .colorCode("#2563eb").periods(periodChoice.periods()).build()).courses(courses).build();
        return new Prepared(request, availableRooms, new RunContext(options.school().schoolCode(), options.snapshot(), List.copyOf(selectedRooms), identities, periodChoice.id()));
    }

    private Options departmentOptions(Options source, Long departmentId) throws Exception {
        var department = catalogs.listDepartments(source.school().schoolCode()).stream().filter(d -> d.id().equals(departmentId) && d.active()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Khoa không thuộc trường hoặc đã ngừng sử dụng."));
        var scoped = source.lecturers().stream().filter(o -> department.id().equals(o.lecturer().departmentId())).toList();
        var ids = new HashSet<Long>(); scoped.forEach(o -> ids.add(o.lecturer().id()));
        return new Options(source.school(), hash(source.snapshot() + ":department:" + departmentId), scoped,
                source.assignments().stream().filter(a -> ids.contains(a.lecturerId())).toList(), source.rooms(), source.periodChoices());
    }

    private static List<LecturerIdentity> previewIdentities(Options options, Set<Long> used, Map<String, Teacher> existing) {
        var result = new ArrayList<LecturerIdentity>();
        for (var row : options.lecturers()) {
            var profile = row.lecturer(); if (!used.contains(profile.id())) continue;
            String id = profile.accountTeacherId() != null ? profile.accountTeacherId() : profile.scheduleTeacherId();
            if (id == null) {
                id = "DRAFT_L" + profile.id();
                if (existing.containsKey(id)) id = "DRAFT_" + UUID.randomUUID().toString().replace("-", "");
            }
            result.add(new LecturerIdentity(profile.id(), profile.lecturerCode(), id, profile.accountTeacherId(), profile.lecturerType().name()));
        }
        return result;
    }

    private Context context(String schoolCode) throws Exception {
        String code = CatalogService.schoolCode(schoolCode);
        var school = catalogs.listInstitutions().stream().filter(s -> s.schoolCode().equals(code)).findFirst()
                .orElseThrow(() -> new com.exception.ResourceNotFoundException("Trường chưa có trong danh mục."));
        if (!school.active()) throw new IllegalArgumentException("Trường đã ngừng sử dụng.");
        var allTeachers = new TreeMap<String, Teacher>(); teachers.findAllWithFullSchedule().forEach(t -> allTeachers.put(t.getTeacherId(), t));
        var departments = catalogs.listDepartments(code);
        var departmentActive = new HashMap<Long, Boolean>(); departments.forEach(d -> departmentActive.put(d.id(), d.active()));
        var profiles = catalogs.listLecturers(code);
        var options = new ArrayList<LecturerOption>();
        for (var profile : profiles) {
            String reason = null;
            var account = profile.accountTeacherId() == null ? null : allTeachers.get(profile.accountTeacherId());
            var projection = profile.scheduleTeacherId() == null ? null : allTeachers.get(profile.scheduleTeacherId());
            if (!profile.active()) reason = "Giảng viên đã ngừng sử dụng";
            else if (profile.departmentId() != null && !Boolean.TRUE.equals(departmentActive.get(profile.departmentId()))) reason = "Khoa đã ngừng sử dụng";
            else if (profile.accountTeacherId() != null && (account == null || !"TEACHER".equals(account.getRole()) || account.getUsername() == null || account.getUsername().isBlank()))
                reason = "Tài khoản liên kết không còn là tài khoản GV hợp lệ";
            else if (profile.scheduleTeacherId() != null && projection == null) reason = "Thiếu hồ sơ lịch đã liên kết";
            else if (projection != null && !"TEACHER".equals(projection.getRole())) reason = "Hồ sơ lịch không còn là hồ sơ giảng viên";
            else if (projection != null && account != null && !projection.getTeacherId().equals(account.getTeacherId())) reason = "Cần đối chiếu hồ sơ lịch và tài khoản";
            else if (projection != null && account == null && projection.getUsername() != null && !projection.getUsername().isBlank()) reason = "Hồ sơ lịch đã có tài khoản; cần liên kết đúng tài khoản GV";
            else if (account == null && projection == null) {
                var old = allTeachers.get(profile.lecturerCode());
                if (old == null) old = allTeachers.values().stream().filter(t -> t.getTeacherId().equalsIgnoreCase(profile.lecturerCode())).findFirst().orElse(null);
                if (old != null && hasSchoolSchedule(old, code)) reason = "Có lịch cũ dưới mã này; hãy đối chiếu và nối mã hồ sơ lịch cũ trước";
            }
            options.add(new LecturerOption(profile, reason == null, reason));
        }
        var assignments = catalogs.listSchoolAssignments(code);
        var allRooms = catalogs.listRooms(code).stream().sorted(Comparator.comparing(RoomView::id)).toList();
        var choices = periodChoices(code, allTeachers.values());
        var base = new TreeMap<String, Object>();
        base.put("school", school); base.put("lecturers", options); base.put("departments", departments); base.put("assignments", assignments);
        base.put("rooms", allRooms); base.put("periodChoices", choices);
        String snapshot = hash(json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(base));
        return new Context(new Options(school, snapshot, options, assignments.stream().filter(AssignmentView::active).toList(),
                allRooms.stream().filter(RoomView::active).toList(), choices), allTeachers);
    }

    private List<PeriodChoice> periodChoices(String schoolCode, Collection<Teacher> existing) throws Exception {
        var choices = new TreeMap<String, List<PeriodTimeDTO>>();
        for (var teacher : existing) for (var school : teacher.getSchools()) {
            if (!schoolCode.equalsIgnoreCase(school.getSchoolCode())) continue;
            if (school.getPeriods().isEmpty()) {
                if (!school.getCourses().isEmpty()) throw new IllegalArgumentException("Lịch cũ của " + teacher.getTeacherId() + " thiếu bảng giờ; cần sửa trước khi xếp lịch.");
                continue;
            }
            var periods = school.getPeriods().stream().sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber))
                    .map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList();
            ScheduleValidation.periods(periods);
            choices.put(hash(json.writeValueAsString(periods)), periods);
        }
        boolean defaults = choices.isEmpty();
        if (defaults) {
            var periods = ExcelScheduleService.getDefault12PeriodsWithNoonSlot6();
            choices.put(hash(json.writeValueAsString(periods)), periods);
        }
        var result = new ArrayList<PeriodChoice>();
        for (var entry : choices.entrySet()) {
            var p = entry.getValue();
            result.add(new PeriodChoice(entry.getKey(), (defaults ? "Khung giờ mặc định" : "Bảng giờ đã lưu " + (result.size() + 1))
                    + " · " + p.size() + " tiết · " + p.getFirst().getStart() + "–" + p.getLast().getEnd(), p));
        }
        return result;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    List<LecturerIdentity> identities(String schoolCode, Set<Long> ids, LocalDate start) {
        var result = new ArrayList<LecturerIdentity>();
        for (Long id : ids) {
            var profile = lecturers.findByIdAndInstitution_SchoolCode(id, schoolCode).orElseThrow();
            var teacher = profile.getAccount() != null ? profile.getAccount() : profile.getScheduleTeacher();
            if (profile.getAccount() == null) {
                teacher = lecturerAccounts.ensure(profile.getLecturerCode(), profile.getFullName(), teacher, start);
                profile.setAccount(teacher);
            }
            if (profile.getScheduleTeacher() == null || !profile.getScheduleTeacher().getTeacherId().equals(teacher.getTeacherId())) {
                profile.setScheduleTeacher(teacher); lecturers.saveAndFlush(profile);
            }
            result.add(new LecturerIdentity(profile.getId(), profile.getLecturerCode(), teacher.getTeacherId(), profile.getAccount() == null ? null : profile.getAccount().getTeacherId(), profile.getLecturerType().name()));
        }
        return result;
    }

    private static Set<Long> ids(List<Long> values, String label) {
        if (values == null || values.isEmpty() || values.size() > 10000 || values.stream().anyMatch(v -> v == null || v <= 0)
                || new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Cần chọn " + label + " hợp lệ, không lặp.");
        return new LinkedHashSet<>(values);
    }
    private static Map<Long, WeightOverride> overrides(List<WeightOverride> values, List<AssignmentView> assignments, Set<Long> selected) {
        var available = new HashSet<Long>(); assignments.stream().filter(a -> selected.contains(a.lecturerId())).forEach(a -> available.add(a.id()));
        var result = new HashMap<Long, WeightOverride>();
        if (values == null) return result;
        if (values.size() > 10000) throw new IllegalArgumentException("Quá nhiều dòng trọng số.");
        for (var value : values) {
            if (value == null || value.assignmentId() == null || !available.contains(value.assignmentId()) || result.putIfAbsent(value.assignmentId(), value) != null)
                throw new IllegalArgumentException("Trọng số phải thuộc phân công đang hoạt động của GV đã chọn và không được lặp.");
            CatalogService.weight(value.theoryWeight()); CatalogService.weight(value.practiceWeight());
        }
        return result;
    }
    private static boolean hasSchoolSchedule(Teacher teacher, String school) { return teacher.getSchools().stream().anyMatch(s -> school.equalsIgnoreCase(s.getSchoolCode()) && !s.getCourses().isEmpty()); }
    private static String hash(String text) throws java.security.NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
}
