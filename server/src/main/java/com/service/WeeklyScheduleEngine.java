package com.service;

import com.dto.*;
import com.model.*;
import com.repository.TeacherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.service.WeeklyReservations.Reservation;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** One invocation owns all mutable search state; the caller holds the database write lock. */
final class WeeklyScheduleEngine {
    private final TeacherRepository teachers;
    private final ScheduleService schedules;
    private final WeeklyReservations occupied = new WeeklyReservations();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private SchoolWideScheduleRequest request;
    private Map<Integer, PeriodTimeDTO> periods;
    private List<Room> rooms;
    private int probes;
    private int probeLimit;
    private final EnumSet<Blocker> blockers = EnumSet.noneOf(Blocker.class);
    private final Map<ScheduleKey, GeneratedClassSlot> mergedSlots = new LinkedHashMap<>();
    private final Map<ScheduleKey, TeachingCourseDTO> mergedCourses = new LinkedHashMap<>();
    private static final int SEARCH_BUDGET = 2_000_000;

    private enum Blocker {
        SESSION_DAY("ngày LT/TH của GV"), TEACHER("giờ bận của GV"), STUDENTS("lịch của lớp/nhóm"), ROOM("phòng đã có lịch");
        final String label;
        Blocker(String label) { this.label = label; }
    }

    WeeklyScheduleEngine(TeacherRepository teachers, ScheduleService schedules) {
        this.teachers = teachers;
        this.schedules = schedules;
    }

    private record Unit(CourseDemandDTO course, String id, String parent, String label, String sub,
                        int size, boolean practice, int required) {
        String type() { return practice ? "PRACTICE" : "THEORY"; }
        Bucket bucket() { return new Bucket(course.getCourseCode(), course.getCohort(), type()); }
    }
    private record Bucket(String code, String cohort, String type) {}
    private record Assignment(Bucket bucket, String teacher) {}
    private record ScheduleKey(String unit, String teacher, String room, int day, int start, int count) {}
    private record Session(LocalDate date, Room room, int start, int count) {}

    AutoScheduleResponse generate(SchoolWideScheduleRequest input, List<Room> available) {
        request = input;
        validate();
        periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        rooms = available.stream().filter(r -> r != null && r.getCapacity() > 0 && r.getRoomCode() != null && !r.getRoomCode().isBlank()
                        && request.getTargetSchool().getSchoolId().equals(r.getSchoolCode()))
                .sorted(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getRoomCode)).toList();
        loadReservations();
        var units = units();
        // Bound work per unit; a difficult class must not consume every later group's search.
        probeLimit = Math.max(1000, Math.min(100_000, SEARCH_BUDGET / units.size()));
        var buckets = new LinkedHashMap<Bucket, List<Unit>>();
        for (var unit : units) buckets.computeIfAbsent(unit.bucket(), ignored -> new ArrayList<>()).add(unit);
        var allocations = new LinkedHashMap<Assignment, TeacherAllocationDTO>();
        for (var entry : buckets.entrySet()) {
            var candidates = entry.getValue().getFirst().course().getTeachers().stream()
                    .sorted(Comparator.comparing(TeacherCandidateDTO::getTeacherId)).toList();
            var quotas = quotas(entry.getValue().size(), candidates, entry.getKey().type().equals("PRACTICE"));
            for (var teacher : candidates) allocations.put(new Assignment(entry.getKey(), teacher.getTeacherId()),
                    TeacherAllocationDTO.builder().courseCode(entry.getKey().code()).cohort(entry.getKey().cohort())
                            .teacherId(teacher.getTeacherId()).teacherName(teacher.getTeacherName()).sessionType(entry.getKey().type())
                            .weight(weight(teacher, entry.getKey().type().equals("PRACTICE")))
                            .targetClasses(quotas.get(teacher.getTeacherId())).build());
        }
        var slots = new ArrayList<GeneratedClassSlot>();
        var workloads = new ArrayList<ClassWorkloadDTO>();
        var warnings = new ArrayList<String>();
        var byTeacher = new LinkedHashMap<String, List<TeachingCourseDTO>>();
        var names = new HashMap<String, String>();
        // Short windows first; reserve all theory before practice to enforce the different-day policy.
        units.sort(Comparator.comparing(Unit::practice)
                .thenComparingLong(u -> ChronoUnit.DAYS.between(u.course().getStudyStartDate(), u.course().getStudyEndDate()))
                .thenComparing(Unit::id));
        for (var unit : units) {
            probes = 0;
            blockers.clear();
            var candidates = new ArrayList<>(unit.course().getTeachers().stream().filter(t -> weight(t, unit.practice()) > 0).toList());
            candidates.sort(Comparator.<TeacherCandidateDTO>comparingInt(t -> {
                var a = allocations.get(new Assignment(unit.bucket(), t.getTeacherId()));
                return a.getAssignedClasses() - a.getTargetClasses();
            }).thenComparingDouble(t -> {
                var a = allocations.get(new Assignment(unit.bucket(), t.getTeacherId()));
                return (double) a.getAssignedClasses() / Math.max(1, a.getWeight());
            }).thenComparing(TeacherCandidateDTO::getTeacherId));
            List<Session> chosen = List.of();
            TeacherCandidateDTO owner = candidates.isEmpty() ? null : candidates.getFirst();
            int boundary = occupied.size();
            boolean hasRoom = rooms.stream().anyMatch(r -> r.isLab() == unit.practice() && r.getCapacity() >= unit.size());
            search: for (var teacher : hasRoom ? candidates : List.<TeacherCandidateDTO>of()) {
                for (int strategy = 0; strategy < 3; strategy++) {
                    var attempt = arrange(unit, teacher, strategy);
                    if (sum(attempt) > sum(chosen)) { chosen = attempt; owner = teacher; }
                    occupied.rollback(boundary);
                    if (sum(chosen) == unit.required() || probes >= probeLimit) break search;
                }
            }
            int placed = sum(chosen);
            if (owner != null) {
                for (var session : chosen) reserve(unit, owner, session);
                var allocation = allocations.get(new Assignment(unit.bucket(), owner.getTeacherId()));
                if (placed == unit.required()) allocation.setAssignedClasses(allocation.getAssignedClasses() + 1);
                allocation.setScheduledPeriods(allocation.getScheduledPeriods() + placed);
                names.put(owner.getTeacherId(), owner.getTeacherName());
                for (var session : chosen) record(unit, owner, session, slots, byTeacher);
            }
            workloads.add(ClassWorkloadDTO.builder().classId(unit.id()).courseCode(unit.course().getCourseCode())
                    .cohort(unit.course().getCohort()).classGroup(unit.label()).sessionType(unit.type())
                    .teacherId(owner == null ? null : owner.getTeacherId()).requiredPeriods(unit.required())
                    .scheduledPeriods(placed).missingPeriods(unit.required() - placed).build());
            if (placed != unit.required()) {
                String reason;
                if (candidates.isEmpty()) reason = "Không có GV có trọng số " + (unit.practice() ? "TH" : "LT") + " dương.";
                else if (!hasRoom) reason = "Không có phòng " + (unit.practice() ? "LAB" : "LT") + " đủ " + unit.size() + " chỗ trong danh mục được chọn.";
                else if (!blockers.isEmpty()) reason = "Các khung đã thử vướng: "
                        + String.join(", ", blockers.stream().map(b -> b.label).toList()) + ".";
                else reason = "Không còn đủ khung học phù hợp trong khoảng ngày cho phép.";
                warnings.add(unit.course().getCourseCode() + " - " + unit.label() + " - " + unit.type()
                        + ": còn thiếu " + (unit.required() - placed) + "/" + unit.required() + " tiết. " + reason
                        + (probes >= probeLimit ? " Đã đạt giới hạn tìm kiếm riêng của lớp/nhóm này; các lớp/nhóm khác vẫn được xét độc lập." : ""));
            }
        }
        boolean saved = warnings.isEmpty() && !slots.isEmpty();
        if (saved) schedules.replaceWeeklyGenerated(request, byTeacher, names);
        int completed = (int) workloads.stream().filter(w -> w.getMissingPeriods() == 0).count();
        return AutoScheduleResponse.builder().schoolId(request.getTargetSchool().getSchoolId())
                .schoolName(request.getTargetSchool().getSchoolName()).semesterStartDate(request.getSemesterStartDate())
                .periods(request.getTargetSchool().getPeriods()).saved(saved).status(saved ? "SUCCESS" : "NOT_SAVED")
                .message(saved ? "Đã xếp đủ tiết LT/TH theo đề cương và lưu lịch từng tuần."
                        : "Chưa lưu: còn thiếu khối lượng; lịch hiện có được giữ nguyên.")
                .scheduledClasses(slots).classWorkloads(workloads).teacherAllocations(new ArrayList<>(allocations.values()))
                .totalClassesScheduled(completed).totalLessonsScheduled(slots.stream().mapToInt(s -> (int) s.getWeekSchedule().chars().filter(c -> c == '1').count()).sum()).totalRoomsAvailable(rooms.size())
                .unscheduledWarnings(warnings).build();
    }

    private void validate() {
        if (request == null || request.getTargetSchool() == null || request.getCourses() == null
                || request.getCourses().isEmpty() || request.getCourses().size() > 5000)
            throw new IllegalArgumentException("Cần từ 1 đến 5000 dòng kế hoạch.");
        ScheduleValidation.monday(request.getSemesterStartDate());
        ScheduleValidation.text(request.getTargetSchool().getSchoolId(), 50, "Mã trường");
        ScheduleValidation.text(request.getTargetSchool().getSchoolName(), 255, "Tên trường");
        ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        var signatures = new HashMap<String, String>();
        var teacherNames = new HashMap<String, String>();
        for (var c : request.getCourses()) {
            if (c == null) throw new IllegalArgumentException("Dòng kế hoạch trống.");
            String row = "Kế hoạch dòng " + c.getStt() + ": ";
            ScheduleValidation.text(c.getCourseCode(), 50, row + "mã môn");
            ScheduleValidation.text(c.getCourseName(), 180, row + "tên học phần");
            ScheduleValidation.text(c.getCohort(), 50, row + "khóa");
            if (c.getCredits() == null || c.getCredits() < 1 || c.getCredits() > 30
                    || c.getTheoryPeriods() == null || c.getPracticePeriods() == null
                    || c.getTheoryPeriods() < 0 || c.getPracticePeriods() < 0
                    || c.getTheoryPeriods() > 1000 || c.getPracticePeriods() > 1000
                    || c.getTheoryPeriods() + c.getPracticePeriods() == 0)
                throw new IllegalArgumentException(row + "cần tín chỉ và tổng tiết LT/TH theo đề cương (0–1000, không đồng thời bằng 0).");
            if (c.getStudyStartDate() == null || c.getStudyEndDate() == null || c.getStudyEndDate().isBefore(c.getStudyStartDate())
                    || c.getStudyStartDate().isBefore(request.getSemesterStartDate())
                    || c.getStudyEndDate().isAfter(request.getSemesterStartDate().plusWeeks(100).minusDays(1)))
                throw new IllegalArgumentException(row + "ngày bắt đầu/kết thúc không hợp lệ hoặc vượt phạm vi 100 tuần.");
            if (c.getTotalClassesToOpen() < 1 || c.getTotalClassesToOpen() > 500 || c.getClassSize() < 1 || c.getClassSize() > 1000
                    || c.getMaxPeriodsPerSession() < 1 || c.getMaxPeriodsPerSession() > 6
                    || c.getMaxPeriodsPerWeek() < c.getMaxPeriodsPerSession() || c.getMaxPeriodsPerWeek() > 30
                    || (c.getPracticeGroupSize() != null && (c.getPracticeGroupSize() < 1 || c.getPracticeGroupSize() > 1000)))
                throw new IllegalArgumentException(row + "sĩ số, số lớp hoặc giới hạn buổi/tuần/nhóm không hợp lệ.");
            if (c.getPracticeMode() == null || !Set.of("FLEXIBLE", "MIDDLE", "END", "ALTERNATE").contains(c.getPracticeMode()))
                throw new IllegalArgumentException(row + "cách xếp TH phải là FLEXIBLE, MIDDLE, END hoặc ALTERNATE.");
            if ((c.getPracticeStartDate() == null) != (c.getPracticeEndDate() == null))
                throw new IllegalArgumentException(row + "cần đủ hai ngày của khoảng TH riêng.");
            if (c.getPracticeStartDate() != null && (c.getPracticeStartDate().isBefore(c.getStudyStartDate())
                    || c.getPracticeEndDate().isAfter(c.getStudyEndDate()) || c.getPracticeEndDate().isBefore(c.getPracticeStartDate())))
                throw new IllegalArgumentException(row + "khoảng TH phải nằm trong khoảng học chung.");
            if (c.getTeachers() == null || c.getTeachers().isEmpty()) throw new IllegalArgumentException(row + "thiếu danh sách GV.");
            var ids = new HashSet<String>();
            for (var t : c.getTeachers()) {
                if (t == null) throw new IllegalArgumentException(row + "GV trống.");
                ScheduleValidation.text(t.getTeacherId(), 50, row + "mã GV");
                ScheduleValidation.text(t.getTeacherName(), 100, row + "tên GV");
                if (!ids.add(t.getTeacherId()) || t.getTheoryWeight() == null || t.getPracticeWeight() == null
                        || t.getTheoryWeight() < 0 || t.getPracticeWeight() < 0 || t.getTheoryWeight() > 10000 || t.getPracticeWeight() > 10000)
                    throw new IllegalArgumentException(row + "GV lặp hoặc trọng số LT/TH thiếu/sai (0–10000).");
                var oldName = teacherNames.putIfAbsent(t.getTeacherId(), t.getTeacherName());
                if (oldName != null && !oldName.equals(t.getTeacherName())) throw new IllegalArgumentException(row + "tên GV mâu thuẫn.");
            }
            String rowType = (c.getTheoryPeriods() > 0 && c.getPracticePeriods() == 0) ? "LT"
                    : (c.getPracticePeriods() > 0 && c.getTheoryPeriods() == 0) ? "TH" : "BOTH";
            String signature = c.getCourseName() + ":" + c.getCredits()
                    + ":" + c.getTeachers().stream().sorted(Comparator.comparing(TeacherCandidateDTO::getTeacherId))
                    .map(t -> t.getTeacherId() + ":" + t.getTheoryWeight() + ":" + t.getPracticeWeight()).toList();
            var old = signatures.putIfAbsent(c.getCourseCode() + ":" + c.getCohort() + ":" + rowType, signature);
            if (old != null && !old.equals(signature)) throw new IllegalArgumentException(row + "cùng môn/khóa có tên môn, tín chỉ hoặc danh sách GV mâu thuẫn.");
        }
    }

    private List<Unit> units() {
        var result = new ArrayList<Unit>();
        var sequence = new HashMap<String, Integer>();
        long totalPeriods = 0;
        int row = 0;
        int labCapacity = rooms.stream().filter(Room::isLab).mapToInt(Room::getCapacity).max().orElse(0);
        for (var c : request.getCourses()) {
            row++;
            for (int n = 0; n < c.getTotalClassesToOpen(); n++) {
                String parent = c.getCourseCode() + ":" + c.getCohort() + ":" + row + ":" + (n + 1);
                String label = "Lớp %02d".formatted(sequence.merge(c.getCourseCode() + ":" + c.getCohort(), 1, Integer::sum));
                if (c.getTheoryPeriods() > 0) result.add(new Unit(c, parent + ":LT", parent, label, "Cả lớp", c.getClassSize(), false, c.getTheoryPeriods()));
                if (c.getPracticePeriods() > 0) {
                    // With no LAB, retain one demand so placement reports the missing room.
                    int groupCapacity = labCapacity > 0 ? labCapacity : c.getClassSize();
                    if (c.getPracticeGroupSize() != null) groupCapacity = Math.min(groupCapacity, c.getPracticeGroupSize());
                    int groups = (c.getClassSize() + groupCapacity - 1) / groupCapacity;
                    for (int g = 1; g <= groups; g++) result.add(new Unit(c, parent + ":TH:" + g, parent,
                            label + " - Tổ " + g, "Tổ " + g, c.getClassSize() / groups + (g <= c.getClassSize() % groups ? 1 : 0), true, c.getPracticePeriods()));
                }
                if (result.size() > 5000) throw new IllegalArgumentException("Tối đa 5000 lớp/nhóm sau khi tách; hãy chia nhỏ kế hoạch.");
            }
        }
        for (var unit : result) totalPeriods += unit.required();
        if (totalPeriods > 100000) throw new IllegalArgumentException("Tối đa 100000 tiết mỗi lần xếp; hãy chia nhỏ kế hoạch.");
        return result;
    }

    static Map<String, Integer> quotas(int count, List<TeacherCandidateDTO> candidates, boolean practice) {
        var result = new LinkedHashMap<String, Integer>();
        long total = candidates.stream().mapToLong(t -> weight(t, practice)).sum();
        for (var t : candidates) result.put(t.getTeacherId(), 0);
        if (total == 0) return result;
        int assigned = 0;
        for (var t : candidates) {
            int quota = (int) ((long) count * weight(t, practice) / total);
            result.put(t.getTeacherId(), quota); assigned += quota;
        }
        final long divisor = total;
        var order = candidates.stream().filter(t -> weight(t, practice) > 0).sorted(
                Comparator.<TeacherCandidateDTO>comparingLong(t -> -((long) count * weight(t, practice) % divisor))
                        .thenComparing(TeacherCandidateDTO::getTeacherId)).toList();
        for (int i = 0; i < count - assigned; i++) result.merge(order.get(i).getTeacherId(), 1, Integer::sum);
        return result;
    }

    private static int weight(TeacherCandidateDTO teacher, boolean practice) {
        return practice ? teacher.getPracticeWeight() : teacher.getTheoryWeight();
    }

    private List<LocalDate> days(Unit unit) {
        var c = unit.course();
        LocalDate first = c.getStudyStartDate(), last = c.getStudyEndDate();
        if (unit.practice()) {
            if (c.getPracticeStartDate() != null) { first = c.getPracticeStartDate(); last = c.getPracticeEndDate(); }
            else {
                long span = ChronoUnit.DAYS.between(first, last) + 1;
                if ("MIDDLE".equals(c.getPracticeMode())) { first = first.plusDays(span / 3); last = c.getStudyStartDate().plusDays(2 * span / 3); }
                if ("END".equals(c.getPracticeMode())) first = first.plusDays(2 * span / 3);
            }
        }
        var result = new ArrayList<LocalDate>();
        int phase = (int) ChronoUnit.WEEKS.between(request.getSemesterStartDate(), first);
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            int week = (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), day) / 7;
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY && (!unit.practice() || !"ALTERNATE".equals(c.getPracticeMode()) || (week - phase) % 2 == 0)) result.add(day);
        }
        return result;
    }

    private List<Session> arrange(Unit unit, TeacherCandidateDTO teacher, int strategy) {
        var weeks = new TreeMap<Integer, List<LocalDate>>();
        for (var day : days(unit)) weeks.computeIfAbsent((int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), day) / 7,
                ignored -> new ArrayList<>()).add(day);
        var orderedWeeks = new ArrayList<>(weeks.values());
        if (strategy == 2) Collections.reverse(orderedWeeks);
        var result = new ArrayList<Session>();
        int remaining = unit.required();
        for (int i = 0; i < orderedWeeks.size() && remaining > 0 && probes < probeLimit; i++) {
            int target = strategy == 1 ? remaining : (remaining + orderedWeeks.size() - i - 1) / (orderedWeeks.size() - i);
            target = Math.min(Math.min(target, unit.course().getMaxPeriodsPerWeek()), remaining);
            int weekPlaced = 0;
            while (weekPlaced < target) {
                Session found = null;
                int wanted = Math.min(unit.course().getMaxPeriodsPerSession(), target - weekPlaced);
                for (int count = wanted; count >= 1 && found == null && probes < probeLimit; count--) {
                    found = placement(unit, teacher, orderedWeeks.get(i), count, result);
                }
                if (found == null) break;
                reserve(unit, teacher, found); result.add(found);
                weekPlaced += found.count(); remaining -= found.count();
            }
        }
        return result;
    }

    private Session placement(Unit unit, TeacherCandidateDTO teacher, List<LocalDate> dates, int count, List<Session> previous) {
        var sorted = new ArrayList<>(dates);
        if (!previous.isEmpty()) {
            var preferred = previous.getLast().date().getDayOfWeek();
            sorted.sort(Comparator.comparingInt(d -> d.getDayOfWeek() == preferred ? 0 : 1));
        }
        String school = request.getTargetSchool().getSchoolId();
        for (var date : sorted) {
            if (!spendProbe()) return null;
            if (occupied.differentSessionDayBlocked(teacher.getTeacherId(), date, unit.practice())) {
                blockers.add(Blocker.SESSION_DAY);
                continue;
            }
            for (int start : periods.keySet()) {
                if (!spendProbe()) return null;
                if (!ScheduleValidation.hasPeriods(periods, start, count)) continue;
                boolean contiguous = true;
                for (int p = start + 1; p < start + count; p++)
                    if (Duration.between(periods.get(p - 1).getEnd(), periods.get(p).getStart()).toMinutes() > 20) contiguous = false;
                if (!contiguous) continue;
                var begin = periods.get(start).getStart(); var end = periods.get(start + count - 1).getEnd();
                if (occupied.teacherBusy(teacher.getTeacherId(), date, begin, end)) {
                    blockers.add(Blocker.TEACHER); continue;
                }
                if (occupied.studentsBusy(school, unit.parent(), unit.id(), unit.practice(), date, begin, end)) {
                    blockers.add(Blocker.STUDENTS); continue;
                }
                for (var room : rooms) {
                    if (room.isLab() != unit.practice() || room.getCapacity() < unit.size()) continue;
                    if (!spendProbe()) return null;
                    if (!occupied.roomBusy(school, room.getRoomCode(), date, begin, end)) return new Session(date, room, start, count);
                    blockers.add(Blocker.ROOM);
                }
            }
        }
        return null;
    }

    private boolean spendProbe() {
        if (probes >= probeLimit) return false;
        probes++;
        return true;
    }

    private void reserve(Unit unit, TeacherCandidateDTO teacher, Session s) {
        occupied.add(new Reservation(teacher.getTeacherId(), request.getTargetSchool().getSchoolId(), s.room().getRoomCode(),
                s.date(), periods.get(s.start()).getStart(), periods.get(s.start() + s.count() - 1).getEnd(),
                unit.parent(), unit.id(), unit.practice(), true));
    }

    private void loadReservations() {
        var active = new HashSet<String>();
        request.getCourses().forEach(c -> c.getTeachers().forEach(t -> active.add(t.getTeacherId())));
        for (var teacher : teachers.findAllWithFullSchedule()) {
            if (active.contains(teacher.getTeacherId()) && !teacher.getSchools().isEmpty()
                    && !request.getSemesterStartDate().equals(teacher.getSemesterStartDate()))
                throw new IllegalArgumentException("Mốc tuần của " + teacher.getTeacherId() + " khác lịch đã lưu; cần đối chiếu trước.");
            for (var school : teacher.getSchools()) {
                var hours = school.getCourses().isEmpty() ? Map.<Integer, PeriodTimeDTO>of() : ScheduleValidation.periods(school.getPeriods().stream()
                        .map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList());
                for (var c : school.getCourses()) {
                    if (request.getTargetSchool().getSchoolId().equals(school.getSchoolCode()) && "WEEKLY_AUTO".equals(c.getSourceType())) continue;
                    if (!ScheduleValidation.hasPeriods(hours, c.getStartPeriod(), c.getNumPeriods())) throw new IllegalArgumentException("Lịch hiện hữu thiếu bảng tiết.");
                    ScheduleValidation.monday(teacher.getSemesterStartDate());
                    if (c.getWeekSchedule() == null) throw new IllegalArgumentException("Lịch hiện hữu thiếu chuỗi tuần.");
                    for (int w = 0; w < c.getWeekSchedule().length(); w++) if (c.getWeekSchedule().charAt(w) != '-' && c.getWeekSchedule().charAt(w) != ' ') {
                        occupied.add(new Reservation(teacher.getTeacherId(), school.getSchoolCode(), c.getClassroom(),
                                teacher.getSemesterStartDate().plusDays(w * 7L + c.getDayOfWeek() - 2),
                                c.getCustomStartTime() != null ? c.getCustomStartTime() : hours.get(c.getStartPeriod()).getStart(),
                                c.getCustomEndTime() != null ? c.getCustomEndTime() : hours.get(c.getStartPeriod() + c.getNumPeriods() - 1).getEnd(),
                                c.getScheduleParentId(), c.getScheduleClassId(), "PRACTICE".equals(c.getSessionType())
                                || (c.getCourseName() != null && c.getCourseName().startsWith("[TH]")), false));
                    }
                }
            }
        }
    }

    private int sum(List<Session> sessions) { return sessions.stream().mapToInt(Session::count).sum(); }

    private void record(Unit unit, TeacherCandidateDTO teacher, Session s, List<GeneratedClassSlot> slots,
                        Map<String, List<TeachingCourseDTO>> byTeacher) {
        int week = (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), s.date()) / 7;
        String weeks = "-".repeat(week) + "1";
        var key = new ScheduleKey(unit.id(), teacher.getTeacherId(), s.room().getRoomCode(), s.date().getDayOfWeek().getValue() + 1, s.start(), s.count());
        if (mergedSlots.containsKey(key)) {
            String old = mergedSlots.get(key).getWeekSchedule();
            var markers = Arrays.copyOf(old.toCharArray(), Math.max(old.length(), week + 1));
            for (int i = old.length(); i < markers.length; i++) markers[i] = '-';
            markers[week] = '1';
            String combined = new String(markers);
            mergedSlots.get(key).setWeekSchedule(combined);
            mergedSlots.get(key).setPhaseNote("Các tuần có cùng thứ, tiết và phòng");
            mergedCourses.get(key).setWeekSchedule(combined);
            return;
        }
        String display = (unit.practice() ? "[TH] " : "[LT] ") + unit.course().getCourseName() + " (Khóa "
                + unit.course().getCohort() + " - " + unit.label() + ")";
        String snapshot;
        try { snapshot = json.writeValueAsString(unit.course()); }
        catch (Exception e) { throw new IllegalStateException("Không thể lưu dữ liệu kế hoạch.", e); }
        slots.add(GeneratedClassSlot.builder().classId(unit.id()).parentClassId(unit.parent())
                .studyStartDate(unit.course().getStudyStartDate()).studyEndDate(unit.course().getStudyEndDate())
                .baseCourseName(unit.course().getCourseName()).credits(unit.course().getCredits()).courseCode(unit.course().getCourseCode())
                .courseName(display).cohort(unit.course().getCohort()).classGroup(unit.label()).subGroup(unit.sub()).sessionType(unit.type())
                .teacherId(teacher.getTeacherId()).teacherName(teacher.getTeacherName()).studentCount(unit.size())
                .room(s.room().getRoomCode()).roomType(s.room().getRoomType()).roomCapacity(s.room().getCapacity())
                .dayOfWeek(s.date().getDayOfWeek().getValue() + 1).startPeriod(s.start()).numPeriods(s.count()).weekSchedule(weeks)
                .phaseNote("Ngày " + s.date()).build());
        byTeacher.computeIfAbsent(teacher.getTeacherId(), ignored -> new ArrayList<>()).add(TeachingCourseDTO.builder()
                .courseCode(unit.course().getCourseCode()).courseName(display).room(s.room().getRoomCode())
                .dayOfWeek(s.date().getDayOfWeek().getValue() + 1).startPeriod(s.start()).numPeriods(s.count()).weekSchedule(weeks)
                .sourceType("WEEKLY_AUTO").scheduleClassId(unit.id()).scheduleParentId(unit.parent()).sessionType(unit.type()).planSnapshot(snapshot).build());
        mergedSlots.put(key, slots.getLast());
        mergedCourses.put(key, byTeacher.get(teacher.getTeacherId()).getLast());
    }
}
