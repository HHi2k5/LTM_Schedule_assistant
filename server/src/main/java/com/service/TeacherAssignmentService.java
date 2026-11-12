package com.service;

import com.dto.AssignmentView.*;
import com.dto.PeriodTimeDTO;
import com.dto.TeacherCandidateDTO;
import com.exception.ResourceNotFoundException;
import com.model.*;
import com.repository.*;
import com.security.ScheduleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Trưởng bộ môn phân công giảng viên vào các lớp khoa đã xếp phòng.
 * Lớp chưa phân công nằm dưới giảng viên hệ thống {@link Teacher#UNASSIGNED_ID}; phân công là chuyển
 * các buổi của lớp sang lịch của giảng viên, bỏ phân công là chuyển ngược lại.
 */
@Service
@RequiredArgsConstructor
public class TeacherAssignmentService {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT_TO_DEPT = "SENT_TO_DEPT";
    public static final String STATUS_SUBMITTED_TO_ADMIN = "SUBMITTED_TO_ADMIN";

    private static final Set<String> DEPARTMENT_SOURCES = Set.of("WEEKLY_AUTO", "VISITING_AUTO");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final TeacherRepository teachers;
    private final SchoolRepository schools;
    private final SubjectRepository subjects;
    private final DepartmentRepository departments;
    private final TeacherSubjectRepository teacherSubjects;
    private final TeacherUnavailabilityRepository unavailabilityRepo;
    private final ScheduleService scheduleService;
    private final ScheduleAccess access;
    private final ScheduleWriteLock writeLock;
    private final ScheduleChangePublisher changes;

    /** Một buổi dạy thật trên lịch (ngày + giờ), dùng để phát hiện trùng giờ giữa các trường/đơn vị. */
    private record Occurrence(LocalDate date, LocalTime start, LocalTime end, String classId, String label) {
        boolean overlaps(Occurrence other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    private static final class ScheduledClass {
        String classId;
        String courseCode;
        String label;
        String sessionType;
        String workflowStatus;
        Teacher owner;
        School school;
        List<TeachingCourse> rows = new ArrayList<>();
        List<Occurrence> occurrences = new ArrayList<>();

        boolean practice() {
            return "PRACTICE".equals(sessionType) || (label != null && label.startsWith("[TH]"));
        }
    }

    private record Qualified(Teacher teacher, String kind, int theoryWeight, int practiceWeight) {
        int weight(boolean practice) { return practice ? practiceWeight : theoryWeight; }
        boolean primary() { return TeacherSubject.KIND_PRIMARY.equals(kind); }
    }

    private static int dayOfWeekNumber(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? 8 : date.getDayOfWeek().getValue() + 1;
    }

    private static String dayLabel(int dow) {
        return dow == 8 ? "Chủ Nhật" : "Thứ " + dow;
    }

    /** Ảnh chụp lịch toàn khoa trong một giao dịch; được cập nhật theo từng lần chuyển lớp. */
    private final class Snapshot {
        final String schoolCode;
        final Map<String, Teacher> teacherById = new LinkedHashMap<>();
        final Map<String, ScheduledClass> classes = new TreeMap<>();
        final Map<String, Map<LocalDate, List<Occurrence>>> busy = new HashMap<>();
        final Map<String, Subject> catalog = new HashMap<>();
        final Map<Long, Department> departmentById = new HashMap<>();
        final Map<String, List<TeacherSubject>> qualifiedRows = new HashMap<>();
        final Map<String, List<TeacherUnavailability>> unavailabilityByTeacher = new HashMap<>();

        Snapshot(String schoolCode) {
            this.schoolCode = schoolCode;
            for (Subject subject : subjects.findBySchoolCodeOrderByCourseCodeAsc(schoolCode)) {
                catalog.put(subject.getCourseCode().toUpperCase(Locale.ROOT), subject);
            }
            for (Department department : departments.findBySchoolCodeOrderByCodeAsc(schoolCode)) {
                departmentById.put(department.getId(), department);
            }
            for (TeacherSubject row : teacherSubjects.findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc(schoolCode)) {
                qualifiedRows.computeIfAbsent(row.getCourseCode().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(row);
            }
            for (TeacherUnavailability u : unavailabilityRepo.findBySchoolCode(schoolCode)) {
                if (u.getTeacherId() == null) continue;
                unavailabilityByTeacher.computeIfAbsent(u.getTeacherId().trim().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(u);
            }
            var seenCourseIds = new HashSet<Long>();
            for (Teacher teacher : teachers.findAllWithFullSchedule()) {
                teacherById.put(teacher.getTeacherId(), teacher);
                if (teacher.getSchools() == null) continue;
                for (School school : teacher.getSchools()) {
                    if (school.getCourses() == null) continue;
                    for (TeachingCourse course : school.getCourses()) {
                        if (course.getId() != null && !seenCourseIds.add(course.getId())) continue;
                        var times = occurrences(teacher, school, course);
                        if (!teacher.unassignedPool()) times.forEach(time -> occupy(teacher.getTeacherId(), time));
                        if (!schoolCode.equals(school.getSchoolCode()) || course.getScheduleClassId() == null
                                || course.getSourceType() == null || !DEPARTMENT_SOURCES.contains(course.getSourceType())) continue;
                        ScheduledClass scheduled = classes.computeIfAbsent(course.getScheduleClassId(), id -> {
                            var created = new ScheduledClass();
                            created.classId = id;
                            created.courseCode = course.getCourseCode() == null ? "" : course.getCourseCode().toUpperCase(Locale.ROOT);
                            created.label = course.getCourseName();
                            created.sessionType = course.getSessionType();
                            created.workflowStatus = normalizeStatus(course.getWorkflowStatus());
                            created.owner = teacher;
                            created.school = school;
                            return created;
                        });
                        if (scheduled.owner != teacher) continue; // Dữ liệu cũ bị chia đôi: giữ theo giảng viên đầu tiên.
                        scheduled.rows.add(course);
                        scheduled.occurrences.addAll(times);
                    }
                }
            }
        }

        void occupy(String teacherId, Occurrence time) {
            busy.computeIfAbsent(teacherId, ignored -> new HashMap<>())
                    .computeIfAbsent(time.date(), ignored -> new ArrayList<>()).add(time);
        }

        void release(String teacherId, String classId) {
            var days = busy.get(teacherId);
            if (days != null) days.values().forEach(list -> list.removeIf(time -> classId.equals(time.classId())));
        }

        Long departmentOf(ScheduledClass scheduled) {
            Subject subject = catalog.get(scheduled.courseCode);
            return subject == null ? null : subject.getDepartmentId();
        }

        /** Giảng viên được gắn môn của lớp và thuộc bộ môn của môn đó. */
        List<Qualified> qualified(ScheduledClass scheduled) {
            Long department = departmentOf(scheduled);
            var result = new LinkedHashMap<String, Qualified>();
            for (TeacherSubject row : qualifiedRows.getOrDefault(scheduled.courseCode, List.of())) {
                Teacher teacher = teacherById.get(row.getTeacherId().trim().toUpperCase(Locale.ROOT));
                if (teacher == null || teacher.unassignedPool() || Teacher.ROLE_ADMIN.equals(teacher.getRole())) continue;
                if (department != null && !department.equals(teacher.getDepartmentId())) continue;
                int theory = row.getTheoryWeight() == null ? 1 : row.getTheoryWeight();
                int practice = row.getPracticeWeight() == null ? 1 : row.getPracticeWeight();
                String kind = row.substitute() ? TeacherSubject.KIND_SUBSTITUTE : TeacherSubject.KIND_PRIMARY;
                result.merge(teacher.getTeacherId(), new Qualified(teacher, kind, theory, practice), (old, next) -> new Qualified(
                        teacher, old.primary() || next.primary() ? TeacherSubject.KIND_PRIMARY : TeacherSubject.KIND_SUBSTITUTE,
                        Math.max(old.theoryWeight(), next.theoryWeight()), Math.max(old.practiceWeight(), next.practiceWeight())));
            }
            var list = new ArrayList<>(result.values());
            list.sort(Comparator.comparing((Qualified q) -> !q.primary()).thenComparing(q -> q.teacher().getTeacherId()));
            return list;
        }

        String unavailabilityReason(Teacher teacher, LocalDate date) {
            var list = unavailabilityByTeacher.getOrDefault(teacher.getTeacherId().toUpperCase(Locale.ROOT), List.of());
            int dow = dayOfWeekNumber(date);
            for (TeacherUnavailability u : list) {
                String suffix = (u.getNote() != null && !u.getNote().isBlank()) ? " (" + u.getNote() + ")" : "";
                if (u.getSpecificDate() != null && u.getSpecificDate().equals(date)) {
                    return "Bận ngày " + date.format(DAY) + suffix;
                }
                if (u.getSpecificDate() == null && u.getDayOfWeek() != null && u.getDayOfWeek() == dow) {
                    return "Bận cố định " + dayLabel(dow) + suffix;
                }
            }
            return null;
        }

        /** null nếu giảng viên nhận được lớp; ngược lại là lý do (trùng giờ, bận ngày/thứ, lệch mốc tuần). */
        String blockedReason(ScheduledClass scheduled, Teacher teacher) {
            var days = busy.getOrDefault(teacher.getTeacherId(), Map.of());
            for (Occurrence time : scheduled.occurrences) {
                String unavailable = unavailabilityReason(teacher, time.date());
                if (unavailable != null) return unavailable;
                for (Occurrence other : days.getOrDefault(time.date(), List.of())) {
                    if (scheduled.classId.equals(other.classId()) || !time.overlaps(other)) continue;
                    return "Trùng giờ ngày " + time.date().format(DAY) + " (" + time.start() + "–" + time.end() + ") với "
                            + other.label();
                }
            }
            try {
                long shift = weekShift(scheduled.owner, teacher);
                for (TeachingCourse row : scheduled.rows) shiftWeeks(row.getWeekSchedule(), shift);
            } catch (IllegalArgumentException mismatch) {
                return mismatch.getMessage();
            }
            return null;
        }
    }

    private static LocalTime periodTime(School school, int period, boolean start) {
        if (school.getPeriods() == null) return null;
        for (SchoolPeriod item : school.getPeriods()) {
            if (item != null && item.getPeriodNumber() == period) return start ? item.getStartTime() : item.getEndTime();
        }
        return null;
    }

    private static List<Occurrence> occurrences(Teacher teacher, School school, TeachingCourse course) {
        LocalDate anchor = teacher.getSemesterStartDate();
        String weeks = course.getWeekSchedule();
        LocalTime start = course.getCustomStartTime() != null ? course.getCustomStartTime()
                : periodTime(school, course.getStartPeriod(), true);
        LocalTime end = course.getCustomEndTime() != null ? course.getCustomEndTime()
                : periodTime(school, course.getStartPeriod() + course.getNumPeriods() - 1, false);
        // Lịch thiếu bảng giờ không thể so trùng; bỏ qua thay vì chặn toàn bộ trang phân công.
        if (anchor == null || weeks == null || start == null || end == null) return List.of();
        var result = new ArrayList<Occurrence>();
        for (int week = 0; week < weeks.length(); week++) {
            char flag = weeks.charAt(week);
            if (flag == '-' || flag == ' ') continue;
            result.add(new Occurrence(anchor.plusDays(week * 7L + course.getDayOfWeek() - 2), start, end,
                    course.getScheduleClassId(), course.getCourseName()));
        }
        return result;
    }

    /** Số tuần phải lùi chuỗi tuần khi chuyển lớp từ lịch của `from` sang lịch của `to` (hai mốc tuần 1 có thể khác nhau). */
    private static long weekShift(Teacher from, Teacher to) {
        boolean targetEmpty = to.getSchools() == null || to.getSchools().isEmpty();
        if (targetEmpty || to.getSemesterStartDate() == null || from.getSemesterStartDate() == null) return 0;
        long days = ChronoUnit.DAYS.between(to.getSemesterStartDate(), from.getSemesterStartDate());
        if (days % 7 != 0) throw new IllegalArgumentException("Mốc tuần 1 của giảng viên không phải Thứ Hai nên không khớp lịch khoa.");
        return days / 7;
    }

    private static String shiftWeeks(String weeks, long shift) {
        if (weeks == null || shift == 0) return weeks;
        if (shift > 0) {
            if (weeks.length() + shift > 100) throw new IllegalArgumentException("Lịch lớp vượt quá 100 tuần tính từ mốc tuần 1 của giảng viên.");
            return "-".repeat((int) shift) + weeks;
        }
        int cut = (int) -shift;
        String head = weeks.substring(0, Math.min(cut, weeks.length()));
        if (weeks.length() <= cut || !head.replace("-", "").isBlank()) {
            throw new IllegalArgumentException("Mốc tuần 1 trong lịch cá nhân của giảng viên muộn hơn buổi đầu của lớp.");
        }
        return weeks.substring(cut);
    }

    private Long scope(Long requestedDepartment) {
        if (access.isAdmin()) return requestedDepartment;
        Long own = access.headDepartmentId();
        if (own == null) throw new AccessDeniedException("Chỉ trưởng bộ môn hoặc quản trị khoa được phân công giảng viên.");
        return own;
    }

    private static String normalizeStatus(String raw) {
        if (STATUS_SENT_TO_DEPT.equalsIgnoreCase(raw)) return STATUS_SENT_TO_DEPT;
        if (STATUS_SUBMITTED_TO_ADMIN.equalsIgnoreCase(raw)) return STATUS_SUBMITTED_TO_ADMIN;
        return STATUS_DRAFT;
    }

    private void requireEditableByDeptHead(ScheduledClass scheduled) {
        if (!STATUS_SENT_TO_DEPT.equals(scheduled.workflowStatus)) {
            if (STATUS_SUBMITTED_TO_ADMIN.equals(scheduled.workflowStatus)) {
                throw new IllegalArgumentException("Bộ môn đã gửi kết quả phân công lên Admin, không thể chỉnh sửa thêm.");
            }
            throw new IllegalArgumentException("Admin chưa chuyển lịch lớp này về bộ môn.");
        }
    }

    private Teacher requireManageableTeacher(String teacherId, boolean allowSelf) {
        if (teacherId == null || teacherId.isBlank()) throw new IllegalArgumentException("Thiếu mã giảng viên.");
        String normalized = teacherId.trim().toUpperCase(Locale.ROOT);
        Teacher target = teachers.findTeacherWithFullSchedule(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giảng viên " + normalized + "."));
        if (target.unassignedPool() || Teacher.ROLE_ADMIN.equals(target.getRole())) {
            throw new IllegalArgumentException("Không thể thao tác lịch bận cho tài khoản hệ thống/quản trị.");
        }
        if (allowSelf && access.isSelf(target.getTeacherId())) return target;
        Long ownDept = access.headDepartmentId();
        if (ownDept == null || target.getDepartmentId() == null || !ownDept.equals(target.getDepartmentId())) {
            throw new AccessDeniedException("Chỉ trưởng bộ môn mới được thao tác với giảng viên thuộc bộ môn của mình.");
        }
        return target;
    }

    private Teacher requireSelfTeacher(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) throw new IllegalArgumentException("Thiếu mã giảng viên.");
        String normalized = teacherId.trim().toUpperCase(Locale.ROOT);
        Teacher target = teachers.findTeacherWithFullSchedule(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giảng viên " + normalized + "."));
        if (!access.isSelf(target.getTeacherId())) {
            throw new AccessDeniedException("Chỉ chính giảng viên thỉnh giảng mới được tự khai báo hoặc xóa lịch bận của mình.");
        }
        return target;
    }

    private static int totalPeriods(ScheduledClass scheduled) {
        int total = 0;
        for (TeachingCourse row : scheduled.rows) {
            String weeks = row.getWeekSchedule() == null ? "" : row.getWeekSchedule();
            total += row.getNumPeriods() * (int) weeks.chars().filter(flag -> flag != '-' && flag != ' ').count();
        }
        return total;
    }

    // ==================== XEM ====================

    @Transactional(readOnly = true)
    public Overview overview(String schoolId, Long departmentId) {
        String schoolCode = CatalogService.school(schoolId);
        boolean admin = access.isAdmin();
        Long scope = scope(departmentId);
        var snapshot = new Snapshot(schoolCode);

        var classRows = new ArrayList<ClassRow>();
        var loads = new LinkedHashMap<String, int[]>();
        var deptTotal = new HashMap<Long, Integer>();
        var deptAssigned = new HashMap<Long, Integer>();
        var deptStatuses = new HashMap<Long, String>();

        for (ScheduledClass scheduled : snapshot.classes.values()) {
            Long department = snapshot.departmentOf(scheduled);
            boolean visibleToDeptHead = !STATUS_DRAFT.equals(scheduled.workflowStatus);

            if (department != null) {
                deptTotal.merge(department, 1, Integer::sum);
                if (!scheduled.owner.unassignedPool()) {
                    deptAssigned.merge(department, 1, Integer::sum);
                }
                String cur = deptStatuses.get(department);
                if (cur == null) {
                    deptStatuses.put(department, scheduled.workflowStatus);
                } else if (!cur.equals(scheduled.workflowStatus)) {
                    if (STATUS_DRAFT.equals(scheduled.workflowStatus) || STATUS_DRAFT.equals(cur)) {
                        deptStatuses.put(department, STATUS_DRAFT);
                    } else if (STATUS_SENT_TO_DEPT.equals(scheduled.workflowStatus) || STATUS_SENT_TO_DEPT.equals(cur)) {
                        deptStatuses.put(department, STATUS_SENT_TO_DEPT);
                    }
                }
            }

            if (!scheduled.owner.unassignedPool() && (admin || visibleToDeptHead)) {
                int[] load = loads.computeIfAbsent(scheduled.owner.getTeacherId(), ignored -> new int[2]);
                load[0]++;
                load[1] += totalPeriods(scheduled);
            }
            if (scope != null && !scope.equals(department)) continue;
            if (!admin && !visibleToDeptHead) continue;

            var sessions = scheduled.rows.stream()
                    .sorted(Comparator.comparingInt(TeachingCourse::getDayOfWeek).thenComparingInt(TeachingCourse::getStartPeriod))
                    .map(row -> new Session(row.getDayOfWeek(), row.getStartPeriod(), row.getNumPeriods(), row.getClassroom(), row.getWeekSchedule()))
                    .toList();
            var candidates = new ArrayList<Candidate>();
            for (Qualified qualified : snapshot.qualified(scheduled)) {
                boolean current = qualified.teacher() == scheduled.owner;
                candidates.add(new Candidate(qualified.teacher().getTeacherId(), qualified.teacher().getFullName(),
                        qualified.teacher().effectiveType(), qualified.kind(), qualified.weight(scheduled.practice()),
                        current ? null : snapshot.blockedReason(scheduled, qualified.teacher())));
            }
            Subject subject = snapshot.catalog.get(scheduled.courseCode);
            Department owner = department == null ? null : snapshot.departmentById.get(department);
            boolean unassigned = scheduled.owner.unassignedPool();
            classRows.add(new ClassRow(scheduled.classId, scheduled.courseCode,
                    subject == null ? scheduled.label : subject.getCourseName(), scheduled.label,
                    scheduled.practice() ? "PRACTICE" : "THEORY", department, owner == null ? null : owner.getName(),
                    unassigned ? null : scheduled.owner.getTeacherId(), unassigned ? null : scheduled.owner.getFullName(),
                    totalPeriods(scheduled), scheduled.workflowStatus, sessions, candidates));
        }
        classRows.sort(Comparator.comparing(ClassRow::courseCode).thenComparing(ClassRow::sessionType, Comparator.reverseOrder())
                .thenComparing(ClassRow::label, Comparator.nullsLast(Comparator.naturalOrder())));

        var teacherLoads = new ArrayList<TeacherLoad>();
        for (Teacher teacher : snapshot.teacherById.values()) {
            if (teacher.unassignedPool() || teacher.getDepartmentId() == null) continue;
            if (!snapshot.departmentById.containsKey(teacher.getDepartmentId())) continue;
            if (scope != null && !scope.equals(teacher.getDepartmentId())) continue;
            int[] load = loads.getOrDefault(teacher.getTeacherId(), new int[2]);
            teacherLoads.add(new TeacherLoad(teacher.getTeacherId(), teacher.getFullName(), teacher.effectiveType(), load[0], load[1]));
        }
        teacherLoads.sort(Comparator.comparing(TeacherLoad::teacherId));

        var departmentStatusList = new ArrayList<DepartmentStatus>();
        for (Department dept : snapshot.departmentById.values()) {
            Teacher head = snapshot.teacherById.values().stream()
                    .filter(t -> dept.getId().equals(t.getDepartmentId()) && Teacher.ROLE_DEPT_HEAD.equals(t.getRole()))
                    .findFirst().orElse(null);
            int total = deptTotal.getOrDefault(dept.getId(), 0);
            int assigned = deptAssigned.getOrDefault(dept.getId(), 0);
            String st = total == 0 ? "EMPTY" : deptStatuses.getOrDefault(dept.getId(), STATUS_DRAFT);
            departmentStatusList.add(new DepartmentStatus(
                    dept.getId(), dept.getCode(), dept.getName(),
                    head == null ? null : head.getTeacherId(),
                    head == null ? null : head.getFullName(),
                    total, assigned, st
            ));
        }

        String scopeWorkflowStatus = scope != null
                ? (deptTotal.getOrDefault(scope, 0) == 0 ? "EMPTY" : deptStatuses.getOrDefault(scope, STATUS_DRAFT))
                : STATUS_SENT_TO_DEPT;

        Teacher pool = snapshot.teacherById.get(Teacher.UNASSIGNED_ID);
        return new Overview(schoolCode, scope, pool == null ? null : pool.getSemesterStartDate(),
                scopeWorkflowStatus, departmentStatusList, classRows, teacherLoads);
    }

    // ==================== BÀN GIAO LỊCH HAI CHIỀU (ADMIN <-> TRƯỞNG BỘ MÔN) ====================

    @Transactional(rollbackFor = Exception.class)
    public String dispatchToDepartments(String schoolId, Long departmentId) {
        access.requireAdmin();
        writeLock.acquire();
        String schoolCode = CatalogService.school(schoolId);
        var snapshot = new Snapshot(schoolCode);

        Set<School> dirtySchools = new LinkedHashSet<>();
        Set<String> affectedTeachers = new LinkedHashSet<>();
        int updatedClasses = 0;

        for (ScheduledClass scheduled : snapshot.classes.values()) {
            Long dept = snapshot.departmentOf(scheduled);
            if (dept == null) continue;
            if (departmentId != null && !departmentId.equals(dept)) continue;
            if (STATUS_DRAFT.equals(scheduled.workflowStatus)) {
                for (TeachingCourse row : scheduled.rows) {
                    row.setWorkflowStatus(STATUS_SENT_TO_DEPT);
                }
                dirtySchools.add(scheduled.school);
                affectedTeachers.add(scheduled.owner.getTeacherId());
                updatedClasses++;
            }
        }

        if (updatedClasses == 0) {
            throw new IllegalArgumentException(departmentId != null
                    ? "Bộ môn này không có lớp mới ở trạng thái chờ chuyển."
                    : "Không có lớp nào ở trạng thái chờ chuyển về bộ môn.");
        }

        for (School s : dirtySchools) schools.save(s);
        for (String tid : affectedTeachers) changes.changed(tid);

        if (departmentId != null) {
            Department d = snapshot.departmentById.get(departmentId);
            String dName = d != null ? d.getName() : String.valueOf(departmentId);
            return "Đã chuyển " + updatedClasses + " lớp của bộ môn " + dName + " cho Trưởng bộ môn phân công.";
        }
        return "Đã chuyển " + updatedClasses + " lớp về các Trưởng bộ môn để phân công.";
    }

    @Transactional(rollbackFor = Exception.class)
    public String reopenForDepartment(String schoolId, Long departmentId) {
        access.requireAdmin();
        if (departmentId == null) throw new IllegalArgumentException("Vui lòng chọn bộ môn cần mở lại.");
        writeLock.acquire();
        String schoolCode = CatalogService.school(schoolId);
        var snapshot = new Snapshot(schoolCode);

        Set<School> dirtySchools = new LinkedHashSet<>();
        Set<String> affectedTeachers = new LinkedHashSet<>();
        int updatedClasses = 0;

        for (ScheduledClass scheduled : snapshot.classes.values()) {
            Long dept = snapshot.departmentOf(scheduled);
            if (!departmentId.equals(dept)) continue;
            if (!STATUS_SENT_TO_DEPT.equals(scheduled.workflowStatus)) {
                for (TeachingCourse row : scheduled.rows) {
                    row.setWorkflowStatus(STATUS_SENT_TO_DEPT);
                }
                dirtySchools.add(scheduled.school);
                affectedTeachers.add(scheduled.owner.getTeacherId());
                updatedClasses++;
            }
        }

        for (School s : dirtySchools) schools.save(s);
        for (String tid : affectedTeachers) changes.changed(tid);
        Department d = snapshot.departmentById.get(departmentId);
        return "Đã mở lại " + updatedClasses + " lớp của bộ môn " + (d != null ? d.getName() : "") + " cho Trưởng bộ môn.";
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitToAdmin(String schoolId) {
        Long ownDept = access.headDepartmentId();
        if (ownDept == null) throw new AccessDeniedException("Chỉ trưởng bộ môn mới được gửi kết quả phân công lên Admin.");
        writeLock.acquire();
        String schoolCode = CatalogService.school(schoolId);
        var snapshot = new Snapshot(schoolCode);

        Set<School> dirtySchools = new LinkedHashSet<>();
        Set<String> affectedTeachers = new LinkedHashSet<>();
        int submittedClasses = 0;

        for (ScheduledClass scheduled : snapshot.classes.values()) {
            Long dept = snapshot.departmentOf(scheduled);
            if (!ownDept.equals(dept)) continue;
            if (STATUS_DRAFT.equals(scheduled.workflowStatus)) continue;
            for (TeachingCourse row : scheduled.rows) {
                row.setWorkflowStatus(STATUS_SUBMITTED_TO_ADMIN);
            }
            dirtySchools.add(scheduled.school);
            affectedTeachers.add(scheduled.owner.getTeacherId());
            submittedClasses++;
        }

        if (submittedClasses == 0) {
            throw new IllegalArgumentException("Bộ môn chưa có lớp nào được chuyển từ Admin để gửi lại.");
        }

        for (School s : dirtySchools) schools.save(s);
        for (String tid : affectedTeachers) changes.changed(tid);
        return "Đã gửi kết quả phân công (" + submittedClasses + " lớp) lên Admin.";
    }

    // ==================== HỒ SƠ LỊCH BẬN GIẢNG VIÊN THỈNH GIẢNG ====================

    @Transactional(readOnly = true)
    public VisitingProfile visitingProfile(String schoolId, String teacherId) {
        String schoolCode = CatalogService.school(schoolId);
        Teacher target = requireManageableTeacher(teacherId, true);

        String deptName = target.getDepartmentId() == null ? null
                : departments.findById(target.getDepartmentId()).map(Department::getName).orElse(null);

        List<String> subjectCodes = teacherSubjects.findBySchoolCodeAndTeacherIdIgnoreCase(schoolCode, target.getTeacherId())
                .stream()
                .map(ts -> ts.getCourseCode().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();

        List<BusyEntry> busyEntries = unavailabilityRepo
                .findBySchoolCodeAndTeacherIdIgnoreCaseOrderByDayOfWeekAscSpecificDateAsc(schoolCode, target.getTeacherId())
                .stream()
                .map(u -> new BusyEntry(u.getId(), u.getDayOfWeek(), u.getSpecificDate(), u.getNote()))
                .toList();

        List<ExternalSlot> externalSlots = new ArrayList<>();
        if (target.getSchools() != null) {
            for (School s : target.getSchools()) {
                if (s.getCourses() == null) continue;
                for (TeachingCourse c : s.getCourses()) {
                    if (schoolCode.equalsIgnoreCase(s.getSchoolCode()) && ScheduleService.isDepartmentCourse(c)) continue;
                    externalSlots.add(new ExternalSlot(
                            s.getSchoolCode(),
                            s.getSchoolName(),
                            c.getCourseName(),
                            c.getDayOfWeek(),
                            c.getStartPeriod(),
                            c.getNumPeriods(),
                            c.getClassroom(),
                            c.getWeekSchedule()
                    ));
                }
            }
        }
        externalSlots.sort(Comparator.comparingInt(ExternalSlot::dayOfWeek).thenComparingInt(ExternalSlot::startPeriod));

        return new VisitingProfile(
                target.getTeacherId(),
                target.getFullName(),
                target.getDepartmentId(),
                deptName,
                target.effectiveType(),
                subjectCodes,
                busyEntries,
                externalSlots
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public VisitingProfile updateBusyDays(BusyDaysRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin ngày bận.");
        String schoolCode = CatalogService.school(request.schoolId());
        Teacher target = requireSelfTeacher(request.teacherId());
        writeLock.acquire();

        unavailabilityRepo.deleteBySchoolCodeAndTeacherIdIgnoreCaseAndSpecificDateIsNull(schoolCode, target.getTeacherId());
        unavailabilityRepo.flush();

        String note = (request.note() == null || request.note().isBlank()) ? null : request.note().trim();
        if (note != null && note.length() > 255) throw new IllegalArgumentException("Ghi chú tối đa 255 ký tự.");

        Set<Integer> uniqueDays = new TreeSet<>();
        if (request.busyDaysOfWeek() != null) {
            for (Integer d : request.busyDaysOfWeek()) {
                if (d == null || d < 2 || d > 8) throw new IllegalArgumentException("Thứ bận phải từ Thứ 2 (2) đến Chủ Nhật (8).");
                uniqueDays.add(d);
            }
        }

        for (int day : uniqueDays) {
            unavailabilityRepo.save(TeacherUnavailability.builder()
                    .schoolCode(schoolCode)
                    .teacherId(target.getTeacherId())
                    .dayOfWeek(day)
                    .specificDate(null)
                    .note(note)
                    .build());
        }
        return visitingProfile(schoolCode, target.getTeacherId());
    }

    @Transactional(rollbackFor = Exception.class)
    public VisitingProfile addBusyDates(BusyDatesRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu danh sách ngày bận.");
        String schoolCode = CatalogService.school(request.schoolId());
        Teacher target = requireSelfTeacher(request.teacherId());
        if (request.dates() == null || request.dates().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 ngày bận cụ thể.");
        }
        String note = (request.note() == null || request.note().isBlank()) ? null : request.note().trim();
        if (note != null && note.length() > 255) throw new IllegalArgumentException("Ghi chú tối đa 255 ký tự.");

        writeLock.acquire();
        var existingDates = new HashSet<LocalDate>();
        for (TeacherUnavailability u : unavailabilityRepo.findBySchoolCodeAndTeacherIdIgnoreCaseOrderByDayOfWeekAscSpecificDateAsc(schoolCode, target.getTeacherId())) {
            if (u.getSpecificDate() != null) existingDates.add(u.getSpecificDate());
        }
        for (LocalDate d : request.dates()) {
            if (d == null || existingDates.contains(d)) continue;
            unavailabilityRepo.save(TeacherUnavailability.builder()
                    .schoolCode(schoolCode)
                    .teacherId(target.getTeacherId())
                    .dayOfWeek(null)
                    .specificDate(d)
                    .note(note)
                    .build());
            existingDates.add(d);
        }
        return visitingProfile(schoolCode, target.getTeacherId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBusyEntry(Long id) {
        TeacherUnavailability entry = unavailabilityRepo.findById(id).orElse(null);
        if (entry == null) return;
        requireSelfTeacher(entry.getTeacherId());
        writeLock.acquire();
        unavailabilityRepo.delete(entry);
    }

    // ==================== PHÂN CÔNG / BỎ PHÂN CÔNG LỚP ĐÃ MỞ ====================

    @Transactional(rollbackFor = Exception.class)
    public String assign(AssignRequest request) {
        if (request == null || request.classId() == null || request.classId().isBlank()) {
            throw new IllegalArgumentException("Thiếu lớp cần phân công.");
        }
        writeLock.acquire();
        var snapshot = new Snapshot(CatalogService.school(request.schoolId()));
        ScheduledClass scheduled = snapshot.classes.get(request.classId());
        if (scheduled == null) throw new ResourceNotFoundException("Lớp không còn trong lịch khoa. Hãy tải lại trang.");
        Long ownDept = access.headDepartmentId();
        if (ownDept == null) throw new AccessDeniedException("Chỉ trưởng bộ môn mới được thực hiện phân công giảng viên.");
        Long department = snapshot.departmentOf(scheduled);
        if (department == null || !ownDept.equals(department)) {
            throw new AccessDeniedException("Bạn chỉ được phân công các lớp thuộc bộ môn của mình.");
        }
        requireEditableByDeptHead(scheduled);

        boolean unassign = request.teacherId() == null || request.teacherId().isBlank();
        Teacher target;
        if (unassign) {
            scheduleService.ensureUnassignedTeacher(scheduled.owner.getSemesterStartDate());
            target = snapshot.teacherById.computeIfAbsent(Teacher.UNASSIGNED_ID,
                    id -> teachers.findTeacherWithFullSchedule(id).orElseThrow());
        } else {
            String teacherId = request.teacherId().trim().toUpperCase(Locale.ROOT);
            target = snapshot.qualified(scheduled).stream().map(Qualified::teacher)
                    .filter(teacher -> teacher.getTeacherId().equals(teacherId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Giảng viên " + teacherId
                            + " không thuộc bộ môn của môn này hoặc chưa được gắn môn có thể dạy."));
        }
        if (target == scheduled.owner) return unassign ? "Lớp đang ở trạng thái chưa phân công." : "Giảng viên đã được phân công lớp này.";
        if (!unassign) {
            String blocked = snapshot.blockedReason(scheduled, target);
            if (blocked != null) throw new IllegalArgumentException("Không thể phân công " + target.getFullName() + ": " + blocked);
        }
        move(snapshot, scheduled, target);
        return unassign ? "Đã bỏ phân công lớp " + scheduled.label + "."
                : "Đã phân công " + target.getFullName() + " cho lớp " + scheduled.label + ".";
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoResult assignBatch(BatchAssignRequest request) {
        if (request == null || request.teacherId() == null || request.teacherId().isBlank()) {
            throw new IllegalArgumentException("Thiếu mã giảng viên cần phân công.");
        }
        if (request.classIds() == null || request.classIds().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 lớp đã mở để phân công.");
        }
        writeLock.acquire();
        String schoolCode = CatalogService.school(request.schoolId());
        Teacher target = requireManageableTeacher(request.teacherId(), false);
        var snapshot = new Snapshot(schoolCode);

        int assigned = 0;
        var remaining = new ArrayList<Unassigned>();
        for (String classId : request.classIds()) {
            ScheduledClass scheduled = snapshot.classes.get(classId);
            if (scheduled == null) continue;
            Long dept = snapshot.departmentOf(scheduled);
            if (dept == null) access.requireAdmin();
            else access.requireDepartmentAccess(dept);
            requireEditableByDeptHead(scheduled);

            boolean canTeach = snapshot.qualified(scheduled).stream()
                    .anyMatch(q -> q.teacher().getTeacherId().equals(target.getTeacherId()));
            if (!canTeach) {
                remaining.add(new Unassigned(classId, scheduled.label, "Giảng viên không thuộc bộ môn hoặc chưa khai báo dạy môn này."));
                continue;
            }
            if (scheduled.owner == target) continue;

            String blocked = snapshot.blockedReason(scheduled, target);
            if (blocked != null) {
                remaining.add(new Unassigned(classId, scheduled.label, blocked));
                continue;
            }
            move(snapshot, scheduled, target);
            assigned++;
        }

        String msg = "Đã phân công " + assigned + " lớp đã mở cho " + target.getFullName() + "."
                + (remaining.isEmpty() ? "" : " Còn " + remaining.size() + " lớp bị trùng lịch bận.");
        return new AutoResult(remaining.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS", msg, assigned, remaining);
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoResult autoAssignVisiting(String schoolId, String teacherId) {
        writeLock.acquire();
        String schoolCode = CatalogService.school(schoolId);
        Teacher target = requireManageableTeacher(teacherId, false);
        Long department = target.getDepartmentId();
        if (department == null) throw new IllegalArgumentException("Giảng viên chưa được xếp vào bộ môn nào.");
        access.requireDepartmentAccess(department);

        var snapshot = new Snapshot(schoolCode);
        int assigned = 0;
        var remaining = new ArrayList<Unassigned>();

        var pending = snapshot.classes.values().stream()
                .filter(s -> s.owner.unassignedPool() && department.equals(snapshot.departmentOf(s)) && STATUS_SENT_TO_DEPT.equals(s.workflowStatus))
                .filter(s -> snapshot.qualified(s).stream().anyMatch(q -> q.teacher().getTeacherId().equals(target.getTeacherId())))
                .sorted(Comparator.comparing((ScheduledClass s) -> s.courseCode).thenComparing(s -> s.label))
                .toList();

        for (ScheduledClass scheduled : pending) {
            String blocked = snapshot.blockedReason(scheduled, target);
            if (blocked != null) {
                remaining.add(new Unassigned(scheduled.classId, scheduled.label, blocked));
                continue;
            }
            move(snapshot, scheduled, target);
            assigned++;
        }

        String msg = assigned == 0 && remaining.isEmpty()
                ? "Không còn lớp trống nào thuộc chuyên môn của " + target.getFullName() + "."
                : "Đã tự phân công " + assigned + " lớp phù hợp cho " + target.getFullName() + "."
                + (remaining.isEmpty() ? "" : " Có " + remaining.size() + " lớp chưa gán được do trùng lịch bận.");
        return new AutoResult(remaining.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS", msg, assigned, remaining);
    }

    /** Chuyển toàn bộ buổi của lớp sang lịch của giảng viên đích và cập nhật ảnh chụp. */
    private void move(Snapshot snapshot, ScheduledClass scheduled, Teacher target) {
        Teacher from = scheduled.owner;
        School source = scheduled.school;
        long shift = weekShift(from, target);
        if ((target.getSchools() == null || target.getSchools().isEmpty()) && from.getSemesterStartDate() != null
                && !from.getSemesterStartDate().equals(target.getSemesterStartDate())) {
            // Giảng viên chưa có lịch nào: dùng luôn mốc tuần 1 của lịch khoa.
            target.setSemesterStartDate(from.getSemesterStartDate());
            teachers.save(target);
        }
        if (target.getSchools() == null) target.setSchools(new LinkedHashSet<>());
        School destination = target.getSchools().stream()
                .filter(school -> snapshot.schoolCode.equals(school.getSchoolCode())).findFirst().orElse(null);
        if (destination == null) {
            destination = School.builder().schoolCode(source.getSchoolCode()).schoolName(source.getSchoolName())
                    .colorCode(source.getColorCode()).teacher(target).periods(new HashSet<>()).courses(new HashSet<>()).build();
            target.getSchools().add(destination);
        }
        if (destination.getPeriods() == null) destination.setPeriods(new HashSet<>());
        if (destination.getCourses() == null) destination.setCourses(new HashSet<>());

        var moved = new ArrayList<TeachingCourse>();
        for (TeachingCourse row : scheduled.rows) {
            int last = row.getStartPeriod() + row.getNumPeriods() - 1;
            LocalTime start = row.getCustomStartTime() != null ? row.getCustomStartTime() : periodTime(source, row.getStartPeriod(), true);
            LocalTime end = row.getCustomEndTime() != null ? row.getCustomEndTime() : periodTime(source, last, false);
            // Bảng tiết của đích phải có đủ các tiết của lớp; thiếu tiết nào thì lấy từ lịch khoa.
            for (int period = row.getStartPeriod(); period <= last; period++) {
                if (periodTime(destination, period, true) != null) continue;
                LocalTime periodStart = periodTime(source, period, true), periodEnd = periodTime(source, period, false);
                if (periodStart == null || periodEnd == null) continue;
                destination.getPeriods().add(SchoolPeriod.builder().periodNumber(period).startTime(periodStart)
                        .endTime(periodEnd).school(destination).build());
            }
            // Bảng tiết của đích khác giờ lịch khoa: ghim giờ thật của lớp để buổi học không bị lệch.
            boolean sameHours = Objects.equals(start, periodTime(destination, row.getStartPeriod(), true))
                    && Objects.equals(end, periodTime(destination, last, false));
            TeachingCourse copy = TeachingCourse.builder().courseCode(row.getCourseCode()).courseName(row.getCourseName())
                    .classroom(row.getClassroom()).dayOfWeek(row.getDayOfWeek()).startPeriod(row.getStartPeriod())
                    .numPeriods(row.getNumPeriods()).weekSchedule(shiftWeeks(row.getWeekSchedule(), shift))
                    .sourceType(row.getSourceType()).workflowStatus(row.getWorkflowStatus()).scheduleClassId(row.getScheduleClassId())
                    .scheduleParentId(row.getScheduleParentId()).sessionType(row.getSessionType())
                    .planSnapshot(row.getPlanSnapshot())
                    .customStartTime(sameHours ? row.getCustomStartTime() : start)
                    .customEndTime(sameHours ? row.getCustomEndTime() : end)
                    .school(destination).build();
            destination.getCourses().add(copy);
            moved.add(copy);
        }
        source.getCourses().removeIf(row -> scheduled.classId.equals(row.getScheduleClassId()));
        if (!from.unassignedPool() && source.getCourses().isEmpty()) {
            from.getSchools().remove(source);
            schools.delete(source);
        } else {
            schools.save(source);
        }
        School saved = schools.save(destination);
        if (saved != destination) {
            target.getSchools().remove(destination);
            target.getSchools().add(saved);
        }

        snapshot.release(from.getTeacherId(), scheduled.classId);
        if (!target.unassignedPool()) scheduled.occurrences.forEach(time -> snapshot.occupy(target.getTeacherId(), time));
        scheduled.owner = target;
        scheduled.school = saved;
        scheduled.rows = new ArrayList<>(saved == destination ? moved
                : saved.getCourses().stream().filter(row -> scheduled.classId.equals(row.getScheduleClassId())).toList());
        changes.changed(from.getTeacherId());
        changes.changed(target.getTeacherId());
    }

    // ==================== TỰ PHÂN CÔNG ====================

    /**
     * Chia các lớp chưa có giảng viên của bộ môn theo trọng số LT/TH: ưu tiên giảng viên cơ hữu,
     * chỉ dùng giảng viên thỉnh giảng khi không còn giảng viên cơ hữu nào rảnh, và không bao giờ xếp trùng giờ/ngày bận.
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoResult autoAssign(AutoRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin tự phân công.");
        Long department = access.headDepartmentId();
        if (department == null) throw new AccessDeniedException("Chỉ trưởng bộ môn mới được thực hiện tự phân công giảng viên.");
        writeLock.acquire();
        var snapshot = new Snapshot(CatalogService.school(request.schoolId()));

        var buckets = new LinkedHashMap<String, List<ScheduledClass>>();
        for (ScheduledClass scheduled : snapshot.classes.values()) {
            if (!department.equals(snapshot.departmentOf(scheduled))) continue;
            if (!STATUS_SENT_TO_DEPT.equals(scheduled.workflowStatus)) continue;
            buckets.computeIfAbsent(scheduled.courseCode + "|" + (scheduled.practice() ? "TH" : "LT"), ignored -> new ArrayList<>())
                    .add(scheduled);
        }

        int assigned = 0;
        var remaining = new ArrayList<Unassigned>();
        for (var bucket : buckets.values()) {
            var pending = bucket.stream().filter(scheduled -> scheduled.owner.unassignedPool()).toList();
            if (pending.isEmpty()) continue;
            boolean practice = bucket.getFirst().practice();
            var qualified = snapshot.qualified(bucket.getFirst()).stream().filter(q -> q.weight(practice) > 0).toList();
            var regular = qualified.stream().filter(q -> !Teacher.TYPE_VISITING.equals(q.teacher().effectiveType())).toList();
            var visiting = qualified.stream().filter(q -> Teacher.TYPE_VISITING.equals(q.teacher().effectiveType())).toList();

            var counts = new HashMap<String, Integer>();
            for (ScheduledClass scheduled : bucket) {
                if (!scheduled.owner.unassignedPool()) counts.merge(scheduled.owner.getTeacherId(), 1, Integer::sum);
            }
            var quotas = WeeklyScheduleEngine.quotas(bucket.size(), regular.stream().map(q -> TeacherCandidateDTO.builder()
                    .teacherId(q.teacher().getTeacherId()).teacherName(q.teacher().getFullName())
                    .theoryWeight(q.theoryWeight()).practiceWeight(q.practiceWeight()).build()).toList(), practice);

            for (ScheduledClass scheduled : pending) {
                var order = new ArrayList<>(regular);
                order.sort(Comparator.<Qualified>comparingInt(q -> counts.getOrDefault(q.teacher().getTeacherId(), 0)
                                - quotas.getOrDefault(q.teacher().getTeacherId(), 0))
                        .thenComparingDouble(q -> (double) counts.getOrDefault(q.teacher().getTeacherId(), 0) / q.weight(practice))
                        .thenComparing(q -> q.teacher().getTeacherId()));
                var backup = new ArrayList<>(visiting);
                backup.sort(Comparator.<Qualified>comparingInt(q -> counts.getOrDefault(q.teacher().getTeacherId(), 0))
                        .thenComparing(q -> q.teacher().getTeacherId()));
                order.addAll(backup);

                Teacher chosen = null;
                for (Qualified candidate : order) {
                    if (snapshot.blockedReason(scheduled, candidate.teacher()) == null) {
                        chosen = candidate.teacher();
                        break;
                    }
                }
                if (chosen == null) {
                    remaining.add(new Unassigned(scheduled.classId, scheduled.label, qualified.isEmpty()
                            ? "Chưa có giảng viên nào của bộ môn được gắn môn này."
                            : "Mọi giảng viên dạy được môn này đều trùng giờ hoặc bận vào ngày học."));
                    continue;
                }
                move(snapshot, scheduled, chosen);
                counts.merge(chosen.getTeacherId(), 1, Integer::sum);
                assigned++;
            }
        }

        String message = assigned == 0 && remaining.isEmpty() ? "Không còn lớp nào chưa phân công trong bộ môn."
                : "Đã tự phân công " + assigned + " lớp/nhóm."
                + (remaining.isEmpty() ? "" : " Còn " + remaining.size() + " lớp/nhóm chưa phân công được.");
        return new AutoResult(remaining.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS", message, assigned, remaining);
    }
}
