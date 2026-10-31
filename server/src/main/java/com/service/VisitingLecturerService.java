package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.VisitingLecturerDTO.*;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import com.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class VisitingLecturerService {
    private final SchedulingPlanRepository plans;
    private final SchedulingEntryRepository entries;
    private final InstitutionLecturerRepository lecturers;
    private final RoomRepository rooms;
    private final CatalogSchedulingService catalog;
    private final CatalogService directory;
    private final SchedulingPlanService storage;
    private final SchedulingPlanValidation validation;
    private final ScheduleWriteLock lock;
    private final ObjectMapper json;
    private final TeacherRepository teachers;

    public VisitingLecturerDTO.Options options(Long id) throws Exception {
        SchedulingPlanService.admin(); var plan = plan(id); var version = draft(plan);
        var request = request(version); var result = result(version); var source = catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId());
        var rows = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(version.getId()); var choices = new ArrayList<ClassChoice>();
        enrichWorkloads(request, result, rows);
        var labels = new HashMap<String, String>(); result.getCatalogContext().lecturers().forEach(i -> labels.put(i.teacherId(), i.lecturerCode()));
        var groups = new HashMap<String, List<SchedulingEntry>>(); rows.forEach(e -> groups.computeIfAbsent(e.getScheduleClassId(), ignored -> new ArrayList<>()).add(e));
        for (var w : result.getClassWorkloads()) {
            var course = request.getCourses().get(w.getDemandIndex() - 1); var actual = groups.getOrDefault(w.getClassId(), List.of());
            choices.add(new ClassChoice(w.getClassId(), w.getParentClassId() != null ? w.getParentClassId() : actual.isEmpty() ? parent(w.getClassId()) : actual.getFirst().getScheduleParentId(), w.getDemandIndex(), w.getCourseCode(), course.getCourseName(), w.getCohort(), w.getClassGroup(), w.getSessionType(),
                    w.getStudentCount() != null ? w.getStudentCount() : actual.isEmpty() ? course.getClassSize() : actual.getFirst().getStudentCount(), course.getCredits(), course.getStudyStartDate(), course.getStudyEndDate(), w.getRequiredPeriods(), actual.stream().mapToInt(SchedulingEntry::getNumPeriods).sum(),
                    actual.isEmpty() ? w.getTeacherId() == null ? List.of() : List.of(labels.getOrDefault(w.getTeacherId(), w.getTeacherId())) : actual.stream().map(e -> e.getLecturer().getLecturerCode()).distinct().toList()));
        }
        return new VisitingLecturerDTO.Options(version.getId(), plan.getRevision(), source.snapshot(), source.lecturers().stream().filter(o -> o.eligible() && o.lecturer().lecturerType() == LecturerType.VISITING).map(LecturerOption::lecturer).toList(),
                source.assignments(), source.rooms(), request.getTargetSchool().getPeriods(), choices, PublishedBusyReservations.own(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public LecturerView createProfile(Long id, Profile input) throws Exception {
        SchedulingPlanService.admin(); lock.acquire(); var plan = plan(id);
        if (input == null) throw new IllegalArgumentException("Thiếu hồ sơ thỉnh giảng."); revision(plan, input.revision());
        catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId());
        if (input.assignments() == null || input.assignments().isEmpty() || input.assignments().size() > 100) throw new IllegalArgumentException("Cần từ 1 đến 100 phân công môn cho thỉnh giảng.");
        var lecturer = directory.createLecturer(plan.getInstitution().getSchoolCode(), new LecturerInput(input.lecturerCode(), input.fullName(), plan.getDepartment().getId(), LecturerType.VISITING, input.accountTeacherId(), true, null));
        for (var a : input.assignments()) {
            if (a == null) throw new IllegalArgumentException("Phân công môn không được trống.");
            directory.createAssignment(plan.getInstitution().getSchoolCode(), lecturer.id(), new AssignmentInput(a.courseCode(), a.courseName(), a.cohort(), a.theoryWeight(), a.practiceWeight(), true, null));
        }
        return lecturer;
    }

    public Preview preview(Long id, Input input) throws Exception { return build(id, input).preview(); }

    @Transactional(rollbackFor = Exception.class)
    public SchedulingPlanDTO.Detail commit(Long id, Commit command) throws Exception {
        SchedulingPlanService.admin(); lock.acquire();
        if (command == null || command.previewToken() == null) throw new IllegalArgumentException("Cần xem trước đề xuất thỉnh giảng trước khi lưu nháp.");
        var proposal = build(id, command.input());
        if (!MessageDigest.isEqual(proposal.preview().previewToken().getBytes(StandardCharsets.UTF_8), command.previewToken().getBytes(StandardCharsets.UTF_8))) throw stale(id);
        var input = command.input();
        var change = new ChangeInfo(input.baseVersionId(), input.lecturerId(), proposal.preview().lecturerCode(), input.action(), input.timing(), proposal.preview().changes().stream().map(ClassChange::classId).toList(), SchedulingPlanService.admin(), Instant.now());
        return storage.storeVisitingDraft(proposal.plan(), proposal.request(), proposal.config(), proposal.result(), proposal.rows(), change);
    }
    private record Proposal(SchedulingPlan plan, SchoolWideScheduleRequest request, Configuration config, AutoScheduleResponse result, List<SchedulingEntry> rows, Preview preview) {}

    private Proposal build(Long id, Input input) throws Exception {
        String actor = SchedulingPlanService.admin(); var plan = plan(id);
        if (input == null) throw new IllegalArgumentException("Thiếu đề xuất thỉnh giảng."); revision(plan, input.revision());
        var version = draft(plan); if (!version.getId().equals(input.baseVersionId())) throw stale(id);
        var source = catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId());
        if (!source.snapshot().equals(input.snapshot())) throw stale(id);
        var visitor = source.lecturers().stream().filter(o -> o.eligible() && o.lecturer().id().equals(input.lecturerId()) && o.lecturer().lecturerType() == LecturerType.VISITING).map(LecturerOption::lecturer).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chọn thỉnh giảng đang hoạt động, đủ điều kiện và thuộc khoa của bản lịch."));
        if (!Set.of("RECEIVE", "NEW").contains(input.action() == null ? "" : input.action()) || !Set.of("KEEP", "FIXED").contains(input.timing() == null ? "" : input.timing())) throw new IllegalArgumentException("Chọn nhận lớp/thêm lớp và giữ giờ/lịch cố định.");
        var request = request(version); var previous = result(version);
        var original = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(version.getId());
        enrichWorkloads(request, previous, original);
        var rows = new ArrayList<SchedulingEntry>(); original.forEach(e -> { var copy = new SchedulingEntry(); org.springframework.beans.BeanUtils.copyProperties(e, copy); rows.add(copy); });
        List<ClassWorkloadDTO> workloads = json.readValue(json.writeValueAsString(previous.getClassWorkloads()), json.getTypeFactory().constructCollectionType(ArrayList.class, ClassWorkloadDTO.class));
        var byClass = new LinkedHashMap<String, ClassWorkloadDTO>(); for (var w : workloads) byClass.put(w.getClassId(), w);
        var identities = new ArrayList<>(previous.getCatalogContext().lecturers());
        var existingIdentity = identities.stream().filter(i -> i.lecturerId().equals(visitor.id())).findFirst().orElse(null);
        if (existingIdentity != null && !LecturerAccountService.compatible(existingIdentity, visitor)) throw new IllegalArgumentException("Liên kết tài khoản của GV đã đổi; cần đối chiếu danh tính trước khi chuyển lớp.");
        String visitorKey = existingIdentity != null ? existingIdentity.teacherId() : visitor.accountTeacherId() != null ? visitor.accountTeacherId() : visitor.scheduleTeacherId() != null ? visitor.scheduleTeacherId() : virtualKey(plan, visitor.id());
        identities.removeIf(i -> i.lecturerId().equals(visitor.id())); identities.add(new LecturerIdentity(visitor.id(), visitor.lecturerCode(), visitorKey, visitor.accountTeacherId(), "VISITING"));
        var selected = new LinkedHashSet<String>();
        if ("RECEIVE".equals(input.action())) {
            if (input.classIds() == null || input.classIds().isEmpty() || input.classIds().size() > 100 || new HashSet<>(input.classIds()).size() != input.classIds().size() || !byClass.keySet().containsAll(input.classIds()))
                throw new IllegalArgumentException("Chọn 1–100 lớp/nhóm duy nhất của bản nháp hiện tại.");
            if (input.newClass() != null) throw new IllegalArgumentException("Nhận lại lớp không kèm nhu cầu lớp mới."); selected.addAll(input.classIds());
        } else {
            if (!"FIXED".equals(input.timing()) || input.classIds() != null && !input.classIds().isEmpty()) throw new IllegalArgumentException("Lớp mới cần nhu cầu và lịch cố định riêng.");
            String classId = "PLAN_" + plan.getId() + ":VIS_" + hash(json.writeValueAsString(input)).substring(0, 20);
            var w = newClass(plan, input.newClass(), request, workloads, classId, visitorKey); workloads.add(w); byClass.put(w.getClassId(), w); selected.add(w.getClassId());
            if (input.newClass().parentClassId() != null && !input.newClass().parentClassId().isBlank()) {
                var parent = workloads.stream().filter(old -> !old.getClassId().equals(w.getClassId()) && (old.getParentClassId() == null ? parent(old.getClassId()) : old.getParentClassId()).equals(input.newClass().parentClassId()) && "THEORY".equals(old.getSessionType())).findFirst().orElseThrow(() -> new IllegalArgumentException("Chọn lớp LT cha trong cùng bản nháp."));
                var parentCourse = request.getCourses().get(parent.getDemandIndex() - 1);
                if (!"PRACTICE".equals(w.getSessionType()) || !parent.getCourseCode().equalsIgnoreCase(w.getCourseCode()) || !parent.getCohort().equalsIgnoreCase(w.getCohort()) || w.getStudentCount() > parentCourse.getClassSize()) throw new IllegalArgumentException("Nhóm TH phải cùng môn/khóa và không vượt sĩ số lớp cha.");
            }
        }
        var before = original.stream().filter(e -> selected.contains(e.getScheduleClassId())).toList();
        if (before.stream().anyMatch(e -> e.isLocked() && ("FIXED".equals(input.timing()) || !e.getLecturer().getId().equals(visitor.id()))))
            throw new IllegalArgumentException("Lớp/nhóm có buổi đang khóa. Mở khóa buổi trước khi thay giờ hoặc chuyển giảng viên.");
        if (before.size() > 2000) throw new IllegalArgumentException("Chọn tối đa 2000 buổi mỗi đề xuất; thu hẹp số lớp/nhóm.");
        var profile = lecturers.getReferenceById(visitor.id());
        for (String classId : selected) {
            var w = byClass.get(classId); var c = request.getCourses().get(w.getDemandIndex() - 1);
            var assignment = source.assignments().stream().filter(a -> a.lecturerId().equals(visitor.id()) && a.courseCode().equalsIgnoreCase(w.getCourseCode()) && a.cohort().equalsIgnoreCase(w.getCohort())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(w.getCourseCode() + "/" + w.getCohort() + ": thỉnh giảng chưa có phân công môn đang hoạt động."));
            if (("PRACTICE".equals(w.getSessionType()) ? assignment.practiceWeight() : assignment.theoryWeight()) <= 0) throw new IllegalArgumentException(w.getCourseCode() + "/" + w.getCohort() + ": cần trọng số " + w.getSessionType() + " lớn hơn 0.");
            w.setTeacherId(visitorKey); var candidates = new ArrayList<>(c.getTeachers()); candidates.removeIf(t -> visitor.id().equals(t.getCatalogLecturerId()));
            candidates.add(TeacherCandidateDTO.builder().catalogLecturerId(visitor.id()).lecturerCode(visitor.lecturerCode()).teacherId(visitorKey).teacherName(visitor.fullName()).theoryWeight(assignment.theoryWeight()).practiceWeight(assignment.practiceWeight()).build()); c.setTeachers(candidates);
        }
        if ("KEEP".equals(input.timing())) {
            if (input.sessions() != null && !input.sessions().isEmpty()) throw new IllegalArgumentException("Giữ giờ không gửi lịch cố định thay thế.");
            rows.stream().filter(e -> selected.contains(e.getScheduleClassId()) && !e.isLocked()).forEach(e -> { e.setLecturer(profile); e.setLecturerName(visitor.fullName()); });
        } else {
            rows.removeIf(e -> selected.contains(e.getScheduleClassId()));
            fixedRows(input, plan, request, selected, byClass, profile, source.rooms(), original).forEach(rows::add);
        }
        var ownBusy = new ArrayList<>(PublishedBusyReservations.own(request)); ownBusy.removeIf(b -> visitor.id().equals(b.lecturerId())); ownBusy.addAll(expandBusy(input.busySlots(), plan, visitor.id())); request.setBusySlots(ownBusy);
        if (rows.size() > 50000 || ownBusy.size() > 10000) throw new IllegalArgumentException("Bản lịch vượt giới hạn 50000 buổi học hoặc 10000 ngày bận; thu hẹp phạm vi.");
        var oldConfig = json.readValue(version.getConfigurationJson(), Configuration.class); var chosenLecturers = new LinkedHashSet<>(oldConfig.lecturerIds()); chosenLecturers.add(visitor.id()); var chosenRooms = new LinkedHashSet<>(oldConfig.roomIds()); rows.forEach(e -> chosenRooms.add(e.getRoom().getId()));
        var config = new Configuration(source.snapshot(), List.copyOf(chosenLecturers), List.copyOf(chosenRooms), oldConfig.weights(), oldConfig.periodChoiceId(), false);
        var result = VisitingResultBuilder.rebuild(request, previous, rows, workloads, new RunContext(plan.getInstitution().getSchoolCode(), source.snapshot(), config.roomIds(), identities, config.periodChoiceId()));
        if (result.getScheduledClasses().size() > 10000) throw new IllegalArgumentException("Bản lịch vượt 10000 dòng lịch đã gộp; thu hẹp phạm vi.");
        var trial = new SchedulingPlanVersion(); trial.setComplete(result.isComplete());
        var conflicts = validation.check(plan, trial, request, result, rows);
        var changes = new ArrayList<ClassChange>(); var after = rows.stream().filter(e -> selected.contains(e.getScheduleClassId())).toList();
        if (after.size() > 2000) throw new IllegalArgumentException("Tối đa 2000 buổi trong phạm vi đề xuất.");
        for (var classId : selected) {
            var w = byClass.get(classId); var oldRows = before.stream().filter(e -> e.getScheduleClassId().equals(classId)).toList();
            changes.add(new ClassChange(classId, w.getCourseCode(), w.getClassGroup(), w.getSessionType(), oldRows.stream().map(e -> e.getLecturer().getLecturerCode()).distinct().toList(), visitor.lecturerCode(), oldRows.stream().mapToInt(SchedulingEntry::getNumPeriods).sum(), w.getScheduledPeriods(), w.getRequiredPeriods()));
        }
        String token = hash(actor + "\n" + json.writeValueAsString(input) + "\n" + json.writeValueAsString(result) + "\n" + json.writeValueAsString(conflicts));
        var preview = new Preview(token, plan.getRevision(), version.getId(), visitor.lecturerCode(), input.action(), input.timing(), result.isComplete(), result.isComplete() && conflicts.isEmpty(), rows.size() - after.size(), changes,
                sessions(before), sessions(after), ownBusy, conflicts);
        return new Proposal(plan, request, config, result, rows, preview);
    }

    private ClassWorkloadDTO newClass(SchedulingPlan plan, NewClass input, SchoolWideScheduleRequest request, List<ClassWorkloadDTO> workloads, String id, String visitorKey) {
        if (input == null || !Set.of("THEORY", "PRACTICE").contains(input.sessionType() == null ? "" : input.sessionType())) throw new IllegalArgumentException("Lớp mới cần môn, khóa và loại LT/TH.");
        String code = CatalogService.text(input.courseCode(), 50, "Mã môn").toUpperCase(Locale.ROOT), cohort = CatalogService.text(input.cohort(), 50, "Khóa").toUpperCase(Locale.ROOT);
        String name = CatalogService.text(input.courseName(), 180, "Tên môn"), group = CatalogService.text(input.classGroup(), 100, "Lớp/nhóm");
        if (request.getCourses().stream().anyMatch(c -> code.equalsIgnoreCase(c.getCourseCode()) && cohort.equalsIgnoreCase(c.getCohort()) && (!name.equals(c.getCourseName()) || !Objects.equals(input.credits(), c.getCredits())))) throw new IllegalArgumentException("Cùng môn/khóa phải giữ tên môn và tín chỉ của kế hoạch đã lưu.");
        if (workloads.size() >= 5000 || request.getCourses().size() >= 5000 || input.totalPeriods() == null || input.totalPeriods() < 1 || input.totalPeriods() > 1000 || input.studentCount() == null || input.studentCount() < 1 || input.studentCount() > 1000 || input.credits() == null || input.credits() < 1 || input.credits() > 30) throw new IllegalArgumentException("Số lớp, tiết, sĩ số hoặc tín chỉ vượt giới hạn.");
        if (workloads.stream().anyMatch(w -> code.equalsIgnoreCase(w.getCourseCode()) && cohort.equalsIgnoreCase(w.getCohort()) && group.equalsIgnoreCase(w.getClassGroup()) && input.sessionType().equals(w.getSessionType()))) throw new IllegalArgumentException("Lớp/nhóm này đã có trong kế hoạch. Dùng nhận lại lớp để không nhân đôi nhu cầu.");
        date(input.studyStartDate(), plan); date(input.studyEndDate(), plan); if (input.studyEndDate().isBefore(input.studyStartDate())) throw new IllegalArgumentException("Khoảng ngày học không hợp lệ.");
        if (workloads.stream().mapToLong(ClassWorkloadDTO::getRequiredPeriods).sum() + input.totalPeriods() > 100000) throw new IllegalArgumentException("Bản lịch vượt 100000 tiết.");
        var c = CourseDemandDTO.builder().courseCode(code).courseName(name).cohort(cohort).classSize(input.studentCount()).totalClassesToOpen(1).credits(input.credits()).studyStartDate(input.studyStartDate()).studyEndDate(input.studyEndDate())
                .theoryPeriods("THEORY".equals(input.sessionType()) ? input.totalPeriods() : 0).practicePeriods("PRACTICE".equals(input.sessionType()) ? input.totalPeriods() : 0).teachers(new ArrayList<>()).build();
        var courses = new ArrayList<>(request.getCourses()); courses.add(c); request.setCourses(courses);
        request.setTotalWeeks((int) java.time.temporal.ChronoUnit.DAYS.between(request.getSemesterStartDate(), courses.stream().map(CourseDemandDTO::getStudyEndDate).max(LocalDate::compareTo).orElseThrow()) / 7 + 1);
        return ClassWorkloadDTO.builder().demandIndex(courses.size()).classId(id + ("PRACTICE".equals(input.sessionType()) ? ":TH:1" : ":LT")).parentClassId(input.parentClassId() == null || input.parentClassId().isBlank() ? id : input.parentClassId()).studentCount(input.studentCount()).courseCode(code).cohort(cohort).classGroup(group).sessionType(input.sessionType()).teacherId(visitorKey).requiredPeriods(input.totalPeriods()).build();
    }

    private List<SchedulingEntry> fixedRows(Input input, SchedulingPlan plan, SchoolWideScheduleRequest request, Set<String> selected, Map<String, ClassWorkloadDTO> workloads, InstitutionLecturer visitor, List<RoomView> available, List<SchedulingEntry> originalRows) {
        if (input.sessions() == null || input.sessions().size() > 2000) throw new IllegalArgumentException("Lịch cố định cần danh sách tối đa 2000 buổi.");
        var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods()); var allowed = new HashSet<Long>(); available.forEach(r -> allowed.add(r.id()));
        var output = new ArrayList<SchedulingEntry>(); var unique = new HashSet<String>(); long temporary = -1;
        var originalByClass = new HashMap<String, SchedulingEntry>(); originalRows.forEach(e -> originalByClass.putIfAbsent(e.getScheduleClassId(), e));
        for (var session : input.sessions()) {
            if (session == null) throw new IllegalArgumentException("Buổi cố định không được trống.");
            String classId = session.classId(); if ((classId == null || classId.isBlank() || "$NEW".equals(classId)) && selected.size() == 1) classId = selected.iterator().next();
            if (!selected.contains(classId) || session.roomId() == null || !allowed.contains(session.roomId()) || session.startPeriod() == null || session.numPeriods() == null || !ScheduleValidation.hasPeriods(periods, session.startPeriod(), session.numPeriods())) throw new IllegalArgumentException("Buổi cố định phải thuộc lớp đã chọn, dùng phòng đang hoạt động và tiết hợp lệ.");
            date(session.date(), plan); if (!unique.add(classId + ":" + session.date() + ":" + session.startPeriod())) throw new IllegalArgumentException("Lặp cùng lớp, ngày và tiết bắt đầu.");
            var w = workloads.get(classId); var c = request.getCourses().get(w.getDemandIndex() - 1); var demand = new SchedulingDemand(); demand.setDemandIndex(w.getDemandIndex()); demand.setCourseCode(c.getCourseCode()); demand.setCohort(c.getCohort());
            var e = new SchedulingEntry(); e.setId(temporary--); e.setDemand(demand); e.setLecturer(visitor); e.setLecturerName(visitor.getFullName()); e.setRoom(rooms.getReferenceById(session.roomId())); e.setRoomCode(e.getRoom().getRoomCode());
            e.setLessonDate(session.date()); e.setStartPeriod(session.startPeriod()); e.setNumPeriods(session.numPeriods()); e.setStartTime(periods.get(session.startPeriod()).getStart()); e.setEndTime(periods.get(session.startPeriod() + session.numPeriods() - 1).getEnd());
            e.setScheduleClassId(classId); var existing = originalByClass.get(classId); String parent = w.getParentClassId() != null ? w.getParentClassId() : existing == null ? parent(classId) : existing.getScheduleParentId();
            e.setStudentCount(w.getStudentCount() != null ? w.getStudentCount() : existing == null ? c.getClassSize() : existing.getStudentCount());
            e.setScheduleParentId(parent); e.setClassGroup(w.getClassGroup()); e.setSessionType(w.getSessionType());
            e.setCourseName(CatalogService.text(("PRACTICE".equals(w.getSessionType()) ? "[TH] " : "[LT] ") + c.getCourseName() + " (Khóa " + c.getCohort() + " - " + w.getClassGroup() + ")", 255, "Tên buổi dạy")); output.add(e);
        }
        return output;
    }

    private static List<PlanBusyDTO> expandBusy(List<Availability> windows, SchedulingPlan plan, Long visitor) {
        if (windows == null || windows.size() > 100) throw new IllegalArgumentException("Khai báo tối đa 100 khoảng lịch bận, hoặc gửi danh sách rỗng.");
        var result = new LinkedHashMap<String, PlanBusyDTO>();
        for (var w : windows) {
            if (w == null) throw new IllegalArgumentException("Khoảng bận không được trống.");
            date(w.startDate(), plan); var end = w.endDate() == null ? w.startDate() : w.endDate(); date(end, plan);
            if (end.isBefore(w.startDate()) || w.startTime() == null || w.endTime() == null || !w.startTime().isBefore(w.endTime()) || w.dayOfWeek() != null && (w.dayOfWeek() < 2 || w.dayOfWeek() > 8)) throw new IllegalArgumentException("Ngày/giờ hoặc thứ của lịch bận không hợp lệ.");
            if (w.startTime().getNano() != 0 || w.endTime().getNano() != 0) throw new IllegalArgumentException("Giờ bận hỗ trợ chính xác tới giây.");
            if (w.dayOfWeek() == null && !end.equals(w.startDate())) throw new IllegalArgumentException("Khoảng bận nhiều ngày cần chọn thứ lặp hằng tuần.");
            String reason = w.reason() == null || w.reason().isBlank() ? "Lịch bận bên ngoài" : CatalogService.text(w.reason(), 255, "Lý do bận");
            boolean matched = false;
            for (var day = w.startDate(); !day.isAfter(end); day = day.plusDays(1)) if (w.dayOfWeek() == null || day.getDayOfWeek().getValue() + 1 == w.dayOfWeek()) {
                matched = true; result.put(day + ":" + w.startTime() + ":" + w.endTime(), new PlanBusyDTO(visitor, day, w.startTime(), w.endTime(), reason));
            }
            if (!matched) throw new IllegalArgumentException("Khoảng ngày bận không chứa thứ lặp đã chọn.");
        }
        if (result.size() > 2000) throw new IllegalArgumentException("Tối đa 2000 ngày bận trong một đề xuất."); return new ArrayList<>(result.values());
    }
    private static List<Session> sessions(List<SchedulingEntry> rows) { return rows.stream().map(e -> new Session(e.getScheduleClassId(), e.getDemand().getCourseCode(), e.getClassGroup(), e.getLecturer().getLecturerCode(), e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getStartPeriod(), e.getNumPeriods())).toList(); }
    static void enrichWorkloads(SchoolWideScheduleRequest request, AutoScheduleResponse result, List<SchedulingEntry> rows) {
        var first = new HashMap<String, SchedulingEntry>(); rows.forEach(e -> first.putIfAbsent(e.getScheduleClassId(), e));
        for (var w : result.getClassWorkloads()) if (w.getParentClassId() == null) w.setParentClassId(first.containsKey(w.getClassId()) ? first.get(w.getClassId()).getScheduleParentId() : parent(w.getClassId()));
        var groupCounts = new HashMap<String, Integer>(); result.getClassWorkloads().stream().filter(w -> "PRACTICE".equals(w.getSessionType())).forEach(w -> groupCounts.merge(w.getParentClassId(), 1, Integer::sum));
        for (var w : result.getClassWorkloads()) if (w.getStudentCount() == null) {
            var actual = first.get(w.getClassId()); int size = request.getCourses().get(w.getDemandIndex() - 1).getClassSize();
            if (actual != null) size = actual.getStudentCount();
            else if ("PRACTICE".equals(w.getSessionType())) {
                int groups = groupCounts.get(w.getParentClassId()), ordinal;
                try { ordinal = Integer.parseInt(w.getClassId().substring(w.getClassId().lastIndexOf(':') + 1)); } catch (NumberFormatException e) { ordinal = 1; }
                size = size / groups + (ordinal <= size % groups ? 1 : 0);
            }
            w.setStudentCount(size);
        }
    }
    private static void date(LocalDate date, SchedulingPlan plan) { if (date == null || date.isBefore(plan.getSemesterStartDate()) || date.isAfter(plan.getSemesterStartDate().plusWeeks(100).minusDays(1))) throw new IllegalArgumentException("Ngày phải nằm trong 100 tuần của bản lịch."); }
    private static String parent(String classId) { int index = classId.lastIndexOf(":TH:"); return index >= 0 ? classId.substring(0, index) : classId.endsWith(":LT") ? classId.substring(0, classId.length() - 3) : classId; }
    private String virtualKey(SchedulingPlan plan, Long id) {
        String value = "DRAFT_V" + id; int attempt = 0;
        while (teachers.existsById(value)) value = "DRAFT_" + hash(plan.getId() + ":" + plan.getDraftVersion().getId() + ":" + id + ":" + attempt++).substring(0, 32);
        return value;
    }
    private SchedulingPlan plan(Long id) { return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bản lịch không tồn tại.")); }
    private static SchedulingPlanVersion draft(SchedulingPlan plan) { if (plan.getDraftVersion() == null) throw new IllegalArgumentException("Cần xếp và lưu bản nháp trước khi bổ sung thỉnh giảng."); return plan.getDraftVersion(); }
    private SchoolWideScheduleRequest request(SchedulingPlanVersion version) throws Exception { return json.readValue(version.getRequestJson(), SchoolWideScheduleRequest.class); }
    private AutoScheduleResponse result(SchedulingPlanVersion version) throws Exception { return json.readValue(version.getResultJson(), AutoScheduleResponse.class); }
    private static void revision(SchedulingPlan plan, Long expected) { if (expected == null || expected != plan.getRevision()) throw stale(plan.getId()); }
    private static ObjectOptimisticLockingFailureException stale(Long id) { return new ObjectOptimisticLockingFailureException("VisitingPreview", id); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
