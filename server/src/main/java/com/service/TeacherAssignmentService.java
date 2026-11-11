package com.service;

import com.dto.AssignmentView.*;
import com.dto.TeacherCandidateDTO;
import com.exception.ResourceNotFoundException;
import com.model.*;
import com.repository.*;
import com.security.ScheduleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final Set<String> DEPARTMENT_SOURCES = Set.of("WEEKLY_AUTO", "VISITING_AUTO");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final TeacherRepository teachers;
    private final SchoolRepository schools;
    private final SubjectRepository subjects;
    private final DepartmentRepository departments;
    private final TeacherSubjectRepository teacherSubjects;
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

    /** Ảnh chụp lịch toàn khoa trong một giao dịch; được cập nhật theo từng lần chuyển lớp. */
    private final class Snapshot {
        final String schoolCode;
        final Map<String, Teacher> teacherById = new LinkedHashMap<>();
        final Map<String, ScheduledClass> classes = new TreeMap<>();
        final Map<String, Map<LocalDate, List<Occurrence>>> busy = new HashMap<>();
        final Map<String, Subject> catalog = new HashMap<>();
        final Map<Long, Department> departmentById = new HashMap<>();
        final Map<String, List<TeacherSubject>> qualifiedRows = new HashMap<>();

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
            for (Teacher teacher : teachers.findAllWithFullSchedule()) {
                teacherById.put(teacher.getTeacherId(), teacher);
                if (teacher.getSchools() == null) continue;
                for (School school : teacher.getSchools()) {
                    if (school.getCourses() == null) continue;
                    for (TeachingCourse course : school.getCourses()) {
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

        /** Giảng viên được gắn môn của lớp (phụ trách hoặc dạy thay) và thuộc bộ môn của môn đó. */
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

        /** null nếu giảng viên nhận được lớp; ngược lại là lý do (trùng giờ, lệch mốc tuần). */
        String blockedReason(ScheduledClass scheduled, Teacher teacher) {
            var days = busy.getOrDefault(teacher.getTeacherId(), Map.of());
            for (Occurrence time : scheduled.occurrences) {
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
        Long scope = scope(departmentId);
        var snapshot = new Snapshot(schoolCode);

        var classRows = new ArrayList<ClassRow>();
        var loads = new LinkedHashMap<String, int[]>();
        for (ScheduledClass scheduled : snapshot.classes.values()) {
            if (!scheduled.owner.unassignedPool()) {
                int[] load = loads.computeIfAbsent(scheduled.owner.getTeacherId(), ignored -> new int[2]);
                load[0]++;
                load[1] += totalPeriods(scheduled);
            }
            Long department = snapshot.departmentOf(scheduled);
            if (scope != null && !scope.equals(department)) continue;
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
                    totalPeriods(scheduled), sessions, candidates));
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

        Teacher pool = snapshot.teacherById.get(Teacher.UNASSIGNED_ID);
        return new Overview(schoolCode, scope, pool == null ? null : pool.getSemesterStartDate(), classRows, teacherLoads);
    }

    // ==================== PHÂN CÔNG / BỎ PHÂN CÔNG ====================

    @Transactional(rollbackFor = Exception.class)
    public String assign(AssignRequest request) {
        if (request == null || request.classId() == null || request.classId().isBlank()) {
            throw new IllegalArgumentException("Thiếu lớp cần phân công.");
        }
        writeLock.acquire();
        var snapshot = new Snapshot(CatalogService.school(request.schoolId()));
        ScheduledClass scheduled = snapshot.classes.get(request.classId());
        if (scheduled == null) throw new ResourceNotFoundException("Lớp không còn trong lịch khoa. Hãy tải lại trang.");
        Long department = snapshot.departmentOf(scheduled);
        // Môn chưa thuộc bộ môn nào thì chỉ quản trị khoa được phân công.
        if (department == null) access.requireAdmin();
        else access.requireDepartmentAccess(department);

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
                            + " không thuộc bộ môn của môn này hoặc chưa được gắn môn (phụ trách / dạy thay)."));
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
        if (target.getSchools() == null) target.setSchools(new ArrayList<>());
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
                    .sourceType(row.getSourceType()).scheduleClassId(row.getScheduleClassId())
                    .scheduleParentId(row.getScheduleParentId()).sessionType(row.getSessionType())
                    .planSnapshot(row.getPlanSnapshot())
                    .customStartTime(sameHours ? row.getCustomStartTime() : start)
                    .customEndTime(sameHours ? row.getCustomEndTime() : end)
                    .school(destination).build();
            destination.getCourses().add(copy);
            moved.add(copy);
        }
        scheduled.rows.forEach(source.getCourses()::remove);
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
     * Chia các lớp chưa có giảng viên của bộ môn theo trọng số LT/TH: ưu tiên giảng viên phụ trách,
     * chỉ dùng giảng viên dạy thay khi không còn giảng viên phụ trách nào rảnh, và không bao giờ xếp trùng giờ.
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoResult autoAssign(AutoRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin tự phân công.");
        Long department = scope(request.departmentId());
        if (department == null) throw new IllegalArgumentException("Chọn bộ môn cần tự phân công.");
        access.requireDepartmentAccess(department);
        writeLock.acquire();
        var snapshot = new Snapshot(CatalogService.school(request.schoolId()));

        var buckets = new LinkedHashMap<String, List<ScheduledClass>>();
        for (ScheduledClass scheduled : snapshot.classes.values()) {
            if (!department.equals(snapshot.departmentOf(scheduled))) continue;
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
            var primaries = qualified.stream().filter(Qualified::primary).toList();
            var substitutes = qualified.stream().filter(q -> !q.primary()).toList();

            var counts = new HashMap<String, Integer>();
            for (ScheduledClass scheduled : bucket) {
                if (!scheduled.owner.unassignedPool()) counts.merge(scheduled.owner.getTeacherId(), 1, Integer::sum);
            }
            // Chỉ tiêu theo trọng số tính trên toàn bộ lớp của môn, kể cả lớp đã phân công tay trước đó.
            var quotas = WeeklyScheduleEngine.quotas(bucket.size(), primaries.stream().map(q -> TeacherCandidateDTO.builder()
                    .teacherId(q.teacher().getTeacherId()).teacherName(q.teacher().getFullName())
                    .theoryWeight(q.theoryWeight()).practiceWeight(q.practiceWeight()).build()).toList(), practice);

            for (ScheduledClass scheduled : pending) {
                var order = new ArrayList<>(primaries);
                order.sort(Comparator.<Qualified>comparingInt(q -> counts.getOrDefault(q.teacher().getTeacherId(), 0)
                                - quotas.getOrDefault(q.teacher().getTeacherId(), 0))
                        .thenComparingDouble(q -> (double) counts.getOrDefault(q.teacher().getTeacherId(), 0) / q.weight(practice))
                        .thenComparing(q -> q.teacher().getTeacherId()));
                var backup = new ArrayList<>(substitutes);
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
                            ? "Chưa có giảng viên nào của bộ môn được gắn môn này với trọng số " + (practice ? "TH" : "LT") + " dương."
                            : "Mọi giảng viên dạy được môn này đều trùng giờ với lớp."));
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
