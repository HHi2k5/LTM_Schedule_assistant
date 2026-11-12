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
   
    private static final TeacherCandidateDTO UNASSIGNED = TeacherCandidateDTO.builder()
            .teacherId(Teacher.UNASSIGNED_ID).teacherName(Teacher.UNASSIGNED_NAME).theoryWeight(1).practiceWeight(1).build();

    private enum Blocker {
        STUDENTS("lịch của lớp/nhóm"),
        THEORY_DAILY_LIMIT("trần 3 tiết LT/lớp/ngày"),
        PRACTICE_GAP("khoảng cách tối thiểu giữa các buổi TH"),
        ROOM("phòng đã có lịch");

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

    private record CandidatePlan(
            TeacherCandidateDTO teacher,
            List<Session> sessions,
            int placedPeriods,
            boolean complete,
            int gapUsed,
            boolean prefer14ExhaustedBudget,
            int sub14Gaps,
            int multiCourseDays,
            int mixedHalfDays,
            int courseSwitches,
            int sameCourseDayBonus,
            int distinctShapeCount,
            double quotaDeviation
    ) implements Comparable<CandidatePlan> {
        @Override
        public int compareTo(CandidatePlan other) {
            if (this.complete != other.complete) return this.complete ? -1 : 1;
            if (this.placedPeriods != other.placedPeriods) return Integer.compare(other.placedPeriods, this.placedPeriods);
            if (this.sub14Gaps != other.sub14Gaps) return Integer.compare(this.sub14Gaps, other.sub14Gaps);
            if (this.multiCourseDays != other.multiCourseDays) return Integer.compare(this.multiCourseDays, other.multiCourseDays);
            if (this.sameCourseDayBonus != other.sameCourseDayBonus) return Integer.compare(this.sameCourseDayBonus, other.sameCourseDayBonus);
            if (this.mixedHalfDays != other.mixedHalfDays) return Integer.compare(this.mixedHalfDays, other.mixedHalfDays);
            if (this.courseSwitches != other.courseSwitches) return Integer.compare(this.courseSwitches, other.courseSwitches);
            if (this.sessions.size() != other.sessions.size()) return Integer.compare(other.sessions.size(), this.sessions.size());
            if (this.distinctShapeCount != other.distinctShapeCount) return Integer.compare(this.distinctShapeCount, other.distinctShapeCount);
            int quotaCmp = Double.compare(this.quotaDeviation, other.quotaDeviation);
            if (quotaCmp != 0) return quotaCmp;
            return this.teacher.getTeacherId().compareTo(other.teacher.getTeacherId());
        }
    }

    AutoScheduleResponse generate(SchoolWideScheduleRequest input, List<Room> available) {
        request = input;
        validate();
        periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        rooms = available.stream().filter(r -> r != null && r.getCapacity() > 0 && r.getRoomCode() != null && !r.getRoomCode().isBlank()
                        && request.getTargetSchool().getSchoolId().equals(r.getSchoolCode()))
                .sorted(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getRoomCode)).toList();
        loadReservations();
        var units = units();
        probeLimit = Math.max(2000, Math.min(120_000, SEARCH_BUDGET / Math.max(1, units.size())));

        // Không còn chia lớp cho giảng viên ở bước này nên bảng phân bổ luôn rỗng.
        var allocations = new LinkedHashMap<Assignment, TeacherAllocationDTO>();

        var slots = new ArrayList<GeneratedClassSlot>();
        var workloads = new ArrayList<ClassWorkloadDTO>();
        var warnings = new ArrayList<String>();
        var fallbackNotes = new ArrayList<String>();
        var byTeacher = new LinkedHashMap<String, List<TeachingCourseDTO>>();
        var names = new HashMap<String, String>();

        units.sort(Comparator.comparing(Unit::practice)
                .thenComparingLong(u -> ChronoUnit.DAYS.between(u.course().getStudyStartDate(), u.course().getStudyEndDate()))
                .thenComparing(u -> u.course().getCourseCode())
                .thenComparing(Unit::id));

        for (var unit : units) {
            probes = 0;
            blockers.clear();
            var candidates = List.of(UNASSIGNED);

            boolean hasRoom = rooms.stream().anyMatch(r -> roomFits(unit.course(), unit.practice(), r) && r.getCapacity() >= unit.size());
            boolean invalidPracticeTotal = unit.practice() && !SchedulingPolicy.canPartitionPracticePeriods(unit.required());

            CandidatePlan bestPlan = null;
            if (!invalidPracticeTotal && hasRoom) {
                bestPlan = selectBestPlanForUnit(unit, candidates, allocations);
            }

            // A class is committed only when all its periods fit. Failed attempts must not
            // occupy resources or consume a teacher's allocation for the following classes.
            boolean complete = bestPlan != null && bestPlan.complete();
            List<Session> chosen = complete ? bestPlan.sessions() : List.of();
            TeacherCandidateDTO owner = complete ? bestPlan.teacher() : null;
            int placed = sum(chosen);

            if (owner != null && !chosen.isEmpty()) {
                for (var session : chosen) reserve(unit, owner, session);
                names.put(owner.getTeacherId(), owner.getTeacherName());

                String extraNote = null;
                if (unit.practice() && bestPlan.sub14Gaps() > 0) {
                    extraNote = "Lượt dự phòng >= 7 ngày (" + bestPlan.sub14Gaps() + " khoảng dưới 14 ngày)";
                    fallbackNotes.add(unit.course().getCourseCode() + " (" + unit.label() + "): " + extraNote);
                }
                for (var session : chosen) record(unit, owner, session, slots, byTeacher, extraNote);
            }

            String reasonCode = null;
            String reason = null;
            if (placed != unit.required()) {
                String requiredType = requiredRoomType(unit.course(), unit.practice());
                reasonCode = invalidPracticeTotal ? "INVALID_PRACTICE_TOTAL"
                        : !hasRoom ? "NO_ROOM" : "NO_AVAILABLE_SLOT";
                reason = invalidPracticeTotal ? "Tổng tiết TH không chia được 3–4 tiết."
                        : !hasRoom ? "Không có phòng " + (requiredType != null ? "loại " + requiredType
                                : unit.practice() ? "thực hành" : "lý thuyết") + " đủ sức chứa " + unit.size() + " chỗ."
                        : "Chưa xếp được phòng/khung giờ đáp ứng đủ tiết và các điều kiện của lớp."
                                + (probes >= probeLimit ? " Đã đạt giới hạn tìm kiếm của lớp này." : "");
                warnings.add(unit.course().getCourseCode() + " - " + unit.label() + " - " + unit.type()
                        + ": thiếu " + (unit.required() - placed) + "/" + unit.required() + " tiết. " + reason);
            }
            workloads.add(ClassWorkloadDTO.builder().classId(unit.id()).courseCode(unit.course().getCourseCode())
                    .courseName(unit.course().getCourseName()).cohort(unit.course().getCohort())
                    .classGroup(unit.label()).sessionType(unit.type())
                    .teacherId(null).requiredPeriods(unit.required())
                    .scheduledPeriods(placed).missingPeriods(unit.required() - placed)
                    .reasonCode(reasonCode).reason(reason).build());
        }

        var invariantErrors = new ArrayList<String>();
        verifyFinalScheduleInvariants(units, slots, invariantErrors);
        warnings.addAll(invariantErrors);

        boolean saved = invariantErrors.isEmpty() && !slots.isEmpty();
        if (saved) schedules.replaceWeeklyGenerated(request, byTeacher, names);
        int completed = (int) workloads.stream().filter(w -> w.getMissingPeriods() == 0).count();
        var unscheduled = workloads.stream().filter(w -> w.getMissingPeriods() > 0).toList();
        String status = saved ? (unscheduled.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS") : "NOT_SAVED";
        String msg = saved ? "Đã xếp phòng và lưu " + completed + " lớp/nhóm (chưa phân công giảng viên)."
                + (unscheduled.isEmpty() ? "" : " Còn " + unscheduled.size() + " lớp/nhóm chưa xếp được; xem danh sách bên dưới.")
                : "Chưa lưu: " + (slots.isEmpty() ? "không có lớp/nhóm nào xếp đủ tiết." : "kết quả vi phạm điều kiện xếp lịch.");

        return AutoScheduleResponse.builder().schoolId(request.getTargetSchool().getSchoolId())
                .schoolName(request.getTargetSchool().getSchoolName()).semesterStartDate(request.getSemesterStartDate())
                .periods(request.getTargetSchool().getPeriods()).saved(saved).status(status)
                .message(msg).scheduledClasses(slots).classWorkloads(workloads).unscheduledClasses(unscheduled)
                .teacherAllocations(new ArrayList<>(allocations.values()))
                .totalClassesScheduled(completed)
                .totalLessonsScheduled(slots.stream().mapToInt(s -> (int) s.getWeekSchedule().chars().filter(c -> c == '1').count()).sum())
                .totalRoomsAvailable(rooms.size()).unscheduledWarnings(warnings).build();
    }

    private CandidatePlan selectBestPlanForUnit(
            Unit unit,
            List<TeacherCandidateDTO> candidates,
            Map<Assignment, TeacherAllocationDTO> allocations) {

        int boundary = occupied.size();
        var validDays = days(unit);
        if (validDays.isEmpty()) return null;

        if (!unit.practice()) {
            CandidatePlan best = null;
            int perTeacherBudget = Math.max(400, probeLimit / Math.max(1, candidates.size()));
            for (var teacher : candidates) {
                int teacherStartProbes = probes;
                for (int strategy = 0; strategy < 3; strategy++) {
                    if (probes >= probeLimit || probes - teacherStartProbes >= perTeacherBudget) break;
                    var evaluation = arrangeTheorySpread(unit, teacher, validDays, strategy, allocations, boundary);
                    occupied.rollback(boundary);
                    if (best == null || evaluation.compareTo(best) < 0) {
                        best = evaluation;
                    }
                }
            }
            return best;
        }

        var partitions = SchedulingPolicy.practicePartitions(unit.required());
        int firstPassBudget = (probeLimit * 3) / 5;
        CandidatePlan bestPass14 = searchPracticePass(unit, candidates, validDays, partitions,
                SchedulingPolicy.PREFERRED_GAP_DAYS, firstPassBudget, false, allocations, boundary);
        if (bestPass14 != null && bestPass14.complete()) {
            return bestPass14;
        }

        boolean pass14Exhausted = probes >= firstPassBudget;
        CandidatePlan bestPass7 = searchPracticePass(unit, candidates, validDays, partitions,
                SchedulingPolicy.MIN_GAP_DAYS, probeLimit, pass14Exhausted, allocations, boundary);

        if (bestPass7 != null && (bestPass14 == null || bestPass7.compareTo(bestPass14) < 0)) {
            return bestPass7;
        }
        return bestPass14;
    }

    private CandidatePlan searchPracticePass(
            Unit unit,
            List<TeacherCandidateDTO> candidates,
            List<LocalDate> validDays,
            List<List<Integer>> partitions,
            int minGapDays,
            int passProbeCeiling,
            boolean prefer14Exhausted,
            Map<Assignment, TeacherAllocationDTO> allocations,
            int boundary) {

        long spanDays = ChronoUnit.DAYS.between(validDays.getFirst(), validDays.getLast());
        CandidatePlan best = null;

        for (var partition : partitions) {
            long minRequiredSpan = (long) Math.max(0, partition.size() - 1) * minGapDays;
            if (spanDays < minRequiredSpan) continue;
            for (var teacher : candidates) {
                for (int strategy = 0; strategy < 2; strategy++) {
                    if (probes >= passProbeCeiling) break;
                    var plan = arrangePracticePartition(unit, teacher, validDays, partition, minGapDays,
                            strategy, prefer14Exhausted, allocations, boundary, passProbeCeiling);
                    occupied.rollback(boundary);
                    if (best == null || plan.compareTo(best) < 0) {
                        best = plan;
                    }
                }
            }
        }
        return best;
    }

    private CandidatePlan arrangeTheorySpread(
            Unit unit,
            TeacherCandidateDTO teacher,
            List<LocalDate> validDays,
            int strategy,
            Map<Assignment, TeacherAllocationDTO> allocations,
            int boundary) {

        var weeks = new TreeMap<Integer, List<LocalDate>>();
        for (var day : validDays) {
            weeks.computeIfAbsent((int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), day) / 7,
                    ignored -> new ArrayList<>()).add(day);
        }
        var orderedWeeks = new ArrayList<>(weeks.entrySet());
        if (strategy == 2) Collections.reverse(orderedWeeks);

        var result = new ArrayList<Session>();
        int remaining = unit.required();
        int multiCourseDays = 0, mixedHalfDays = 0, switches = 0, sameCourseBonus = 0;

        int maxSessionLen = Math.min(SchedulingPolicy.MAX_THEORY_CLASS_PERIODS_PER_DAY, unit.course().getMaxPeriodsPerSession());

        // Mỗi tuần chỉ xếp 1 ca duy nhất cho đến khi đủ tổng tiết
        for (var entry : orderedWeeks) {
            if (remaining <= 0 || probes >= probeLimit) break;
            int count = Math.min(maxSessionLen, remaining);

            var candidate = findBestSessionInDates(unit, teacher, entry.getValue(), count, result, 0, probeLimit);
            if (candidate != null) {
                var s = candidate.session();
                int[] g = candidate.groupingMetrics();
                multiCourseDays += g[0];
                mixedHalfDays += g[1];
                switches += g[2];
                sameCourseBonus += g[3];

                reserve(unit, teacher, s);
                result.add(s);
                remaining -= s.count();
            }
        }

        // Vòng 2 rải thêm nếu tổng tiết lớn hơn số tuần học
        if (remaining > 0 && probes < probeLimit) {
            for (var entry : orderedWeeks) {
                if (remaining <= 0 || probes >= probeLimit) break;
                int targetWeek = entry.getKey();
                long sessionsInWeek = result.stream()
                        .filter(s -> (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), s.date()) / 7 == targetWeek)
                        .count();
                if (sessionsInWeek >= 2) continue;

                int count = Math.min(maxSessionLen, remaining);
                var candidate = findBestSessionInDates(unit, teacher, entry.getValue(), count, result, 0, probeLimit);
                if (candidate != null) {
                    var s = candidate.session();
                    int[] g = candidate.groupingMetrics();
                    multiCourseDays += g[0];
                    mixedHalfDays += g[1];
                    switches += g[2];
                    sameCourseBonus += g[3];

                    reserve(unit, teacher, s);
                    result.add(s);
                    remaining -= s.count();
                }
            }
        }

        return buildCandidatePlan(unit, teacher, result, 0, false, multiCourseDays, mixedHalfDays, switches, sameCourseBonus, allocations);
    }

    private CandidatePlan arrangePracticePartition(
            Unit unit,
            TeacherCandidateDTO teacher,
            List<LocalDate> validDays,
            List<Integer> sessionLengths,
            int minGapDays,
            int strategy,
            boolean prefer14Exhausted,
            Map<Assignment, TeacherAllocationDTO> allocations,
            int boundary,
            int passProbeCeiling) {

        var orderedDates = new ArrayList<>(validDays);
        if (strategy == 1) Collections.reverse(orderedDates);

        var result = new ArrayList<Session>();
        int multiCourseDays = 0, mixedHalfDays = 0, switches = 0, sameCourseBonus = 0;

        for (int count : sessionLengths) {
            if (probes >= passProbeCeiling) break;
            ScoredSession picked = null;
            if (minGapDays < SchedulingPolicy.PREFERRED_GAP_DAYS) {
                picked = findBestSessionInDates(unit, teacher, orderedDates, count, result,
                        SchedulingPolicy.PREFERRED_GAP_DAYS, passProbeCeiling);
            }
            if (picked == null) {
                picked = findBestSessionInDates(unit, teacher, orderedDates, count, result,
                        minGapDays, passProbeCeiling);
            }
            if (picked == null) break;

            multiCourseDays += picked.groupingMetrics()[0];
            mixedHalfDays += picked.groupingMetrics()[1];
            switches += picked.groupingMetrics()[2];
            sameCourseBonus += picked.groupingMetrics()[3];
            reserve(unit, teacher, picked.session());
            result.add(picked.session());
        }

        result.sort(Comparator.comparing(Session::date).thenComparingInt(Session::start));
        return buildCandidatePlan(unit, teacher, result, minGapDays, prefer14Exhausted,
                multiCourseDays, mixedHalfDays, switches, sameCourseBonus, allocations);
    }

    private record ScoredSession(Session session, int[] groupingMetrics, int shapePenalty, long gapFromExisting) {}

    private ScoredSession findBestSessionInDates(
            Unit unit,
            TeacherCandidateDTO teacher,
            List<LocalDate> dates,
            int count,
            List<Session> previous,
            int minPracticeGapDays,
            int ceiling) {

        String school = request.getTargetSchool().getSchoolId();
        var candidateStarts = SchedulingPolicy.candidateStartPeriods(periods, periods.keySet(), count);
        DayOfWeek preferredDay = previous.isEmpty() ? null : previous.getLast().date().getDayOfWeek();
        Integer preferredStart = previous.isEmpty() ? null : previous.getLast().start();
        String preferredRoom = previous.isEmpty() ? null : previous.getLast().room().getRoomCode();

        ScoredSession best = null;

        for (var date : dates) {
            if (!spendProbe(ceiling)) break;

            int targetWeek = (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), date) / 7;
            int alreadyInWeek = previous.stream()
                    .filter(s -> (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), s.date()) / 7 == targetWeek)
                    .mapToInt(Session::count).sum();
            int maxPerWeek = unit.practice()
                    ? Math.max(unit.course().getMaxPeriodsPerWeek(), SchedulingPolicy.MAX_PRACTICE_SESSION_PERIODS)
                    : unit.course().getMaxPeriodsPerWeek();
            if (alreadyInWeek + count > maxPerWeek) continue;

            if (!unit.practice() && occupied.exceedsTheoryDailyLimit(school, unit.parent(), unit.id(), date, count)) {
                blockers.add(Blocker.THEORY_DAILY_LIMIT);
                continue;
            }

            if (unit.practice() && !occupied.satisfiesPracticeGap(school, unit.id(), date, minPracticeGapDays)) {
                blockers.add(Blocker.PRACTICE_GAP);
                continue;
            }

            for (int start : candidateStarts) {
                if (!spendProbe(ceiling)) break;
                if (!SchedulingPolicy.isContiguousValidSlot(periods, start, count)) continue;

                var begin = periods.get(start).getStart();
                var end = periods.get(start + count - 1).getEnd();

                if (occupied.studentsBusy(school, unit.parent(), unit.id(), unit.practice(), date, begin, end)) {
                    blockers.add(Blocker.STUDENTS);
                    continue;
                }

                // Chưa có giảng viên: ưu tiên khung giờ không trùng với lớp/nhóm khác của cùng môn
                // để sau này bộ môn phân công được với ít giảng viên nhất.
                int sameCourseOverlaps = occupied.sameCourseOverlaps(school, unit.course().getCourseCode(), unit.id(), date, begin, end);

                for (var room : rooms) {
                    if (!roomFits(unit.course(), unit.practice(), room) || room.getCapacity() < unit.size()) continue;
                    if (!spendProbe(ceiling)) break;
                    if (occupied.roomBusy(school, room.getRoomCode(), date, begin, end)) {
                        blockers.add(Blocker.ROOM);
                        continue;
                    }

                    int[] grouping = {sameCourseOverlaps, 0, 0, 0};

                    int shapePenalty = 0;
                    if (preferredDay != null && date.getDayOfWeek() != preferredDay) shapePenalty += 2;
                    if (preferredStart != null && start != preferredStart) shapePenalty += 1;
                    if (preferredRoom != null && !room.getRoomCode().equals(preferredRoom)) shapePenalty += 1;

                    long minGap = Long.MAX_VALUE;
                    if (unit.practice()) {
                        for (LocalDate d : occupied.practiceDatesOfGroup(school, unit.id())) {
                            minGap = Math.min(minGap, Math.abs(d.toEpochDay() - date.toEpochDay()));
                        }
                    }

                    var candidate = new ScoredSession(new Session(date, room, start, count), grouping, shapePenalty, minGap);
                    if (best == null || isBetterSession(candidate, best)) {
                        best = candidate;
                        if (sameCourseOverlaps == 0 && shapePenalty == 0) {
                            return best;
                        }
                    }
                    break;
                }
            }
        }
        return best;
    }

    private boolean isBetterSession(ScoredSession a, ScoredSession b) {
        int[] ga = a.groupingMetrics();
        int[] gb = b.groupingMetrics();
        if (ga[0] != gb[0]) return ga[0] < gb[0];
        if (ga[3] != gb[3]) return ga[3] < gb[3];
        if (ga[1] != gb[1]) return ga[1] < gb[1];
        if (ga[2] != gb[2]) return ga[2] < gb[2];
        if (a.shapePenalty() != b.shapePenalty()) return a.shapePenalty() < b.shapePenalty();
        return a.session().date().isBefore(b.session().date());
    }

    private CandidatePlan buildCandidatePlan(
            Unit unit,
            TeacherCandidateDTO teacher,
            List<Session> sessions,
            int gapUsed,
            boolean prefer14Exhausted,
            int multiCourseDays,
            int mixedHalfDays,
            int switches,
            int sameCourseBonus,
            Map<Assignment, TeacherAllocationDTO> allocations) {

        int placed = sum(sessions);
        boolean complete = (placed == unit.required());
        var allPracticeDates = new ArrayList<LocalDate>();
        if (unit.practice()) {
            for (var s : sessions) allPracticeDates.add(s.date());
        }
        int sub14 = unit.practice() ? SchedulingPolicy.countSubPreferredGaps(allPracticeDates) : 0;
        var shapes = new HashSet<String>();
        for (var s : sessions) {
            shapes.add(s.date().getDayOfWeek().getValue() + ":" + s.start() + ":" + s.room().getRoomCode());
        }
        var alloc = allocations.get(new Assignment(unit.bucket(), teacher.getTeacherId()));
        double deviation = alloc == null ? 0.0 : (double) (alloc.getAssignedClasses() - alloc.getTargetClasses()) / Math.max(1, alloc.getWeight());

        return new CandidatePlan(
                teacher, sessions, placed, complete, gapUsed, prefer14Exhausted,
                sub14, multiCourseDays, mixedHalfDays, switches, sameCourseBonus,
                shapes.size(), deviation
        );
    }

    private void verifyFinalScheduleInvariants(List<Unit> units, List<GeneratedClassSlot> slots, List<String> warnings) {
        var theoryDailyTotals = new HashMap<String, Integer>();
        var practiceDatesByClass = new HashMap<String, List<LocalDate>>();

        for (var slot : slots) {
            if (SchedulingPolicy.isForbiddenStart(periods, slot.getStartPeriod(), slot.getNumPeriods())) {
                warnings.add("Vi phạm chính sách tiết bắt đầu: lớp " + slot.getCourseCode() + " (" + slot.getClassGroup() + ") bắt đầu ở tiết 5 với " + slot.getNumPeriods() + " tiết.");
            }
            boolean isPractice = "PRACTICE".equals(slot.getSessionType());
            if (isPractice && (slot.getNumPeriods() < SchedulingPolicy.MIN_PRACTICE_SESSION_PERIODS
                    || slot.getNumPeriods() > SchedulingPolicy.MAX_PRACTICE_SESSION_PERIODS)) {
                warnings.add("Vi phạm độ dài buổi TH (3–4 tiết): " + slot.getCourseCode() + " (" + slot.getClassGroup() + ") có buổi " + slot.getNumPeriods() + " tiết.");
            }
            String weeks = slot.getWeekSchedule();
            if (weeks == null) continue;
            for (int w = 0; w < weeks.length(); w++) {
                if (weeks.charAt(w) != '-' && weeks.charAt(w) != ' ') {
                    LocalDate date = request.getSemesterStartDate().plusDays(w * 7L + slot.getDayOfWeek() - 2);
                    if (!isPractice) {
                        String key = slot.getClassId() + ":" + date;
                        int total = theoryDailyTotals.merge(key, slot.getNumPeriods(), Integer::sum);
                        if (total > SchedulingPolicy.MAX_THEORY_CLASS_PERIODS_PER_DAY) {
                            warnings.add("Vi phạm trần 3 tiết LT/lớp/ngày: " + slot.getCourseCode() + " (" + slot.getClassGroup() + ") ngày " + date + " có " + total + " tiết.");
                        }
                    } else {
                        practiceDatesByClass.computeIfAbsent(slot.getClassId(), ignored -> new ArrayList<>()).add(date);
                    }
                }
            }
        }

        for (var entry : practiceDatesByClass.entrySet()) {
            var dates = entry.getValue();
            Collections.sort(dates);
            for (int i = 1; i < dates.size(); i++) {
                long gap = dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay();
                if (gap < SchedulingPolicy.MIN_GAP_DAYS) {
                    warnings.add("Vi phạm khoảng cách TH tối thiểu 7 ngày tại nhóm " + entry.getKey() + ": chênh " + gap + " ngày.");
                }
            }
        }
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
            if (c.getPracticePeriods() > 0 && !SchedulingPolicy.canPartitionPracticePeriods(c.getPracticePeriods())) {
                throw new IllegalArgumentException(row + "tổng tiết thực hành (" + c.getPracticePeriods() + ") không thể chia thành các buổi 3–4 tiết.");
            }
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
            // Giảng viên không tham gia bước xếp phòng; danh sách gửi kèm (nếu có) bị bỏ qua.
            c.setTeachers(List.of());
            String rowType = (c.getTheoryPeriods() > 0 && c.getPracticePeriods() == 0) ? "LT"
                    : (c.getPracticePeriods() > 0 && c.getTheoryPeriods() == 0) ? "TH" : "BOTH";
            String signature = c.getCourseName() + ":" + c.getCredits();
            var old = signatures.putIfAbsent(c.getCourseCode() + ":" + c.getCohort() + ":" + rowType, signature);
            if (old != null && !old.equals(signature)) throw new IllegalArgumentException(row + "cùng môn/khóa có tên môn hoặc tín chỉ mâu thuẫn.");
        }
    }

    private List<Unit> units() {
        var result = new ArrayList<Unit>();
        var sequence = new HashMap<String, Integer>();
        long totalPeriods = 0;
        int row = 0;
        for (var c : request.getCourses()) {
            row++;
            // Sức chứa lớn nhất trong các phòng TH mà môn này được phép dùng quyết định số tổ thực hành.
            int labCapacity = rooms.stream().filter(r -> roomFits(c, true, r)).mapToInt(Room::getCapacity).max().orElse(0);
            for (int n = 0; n < c.getTotalClassesToOpen(); n++) {
                String parent = c.getCourseCode() + ":" + c.getCohort() + ":" + row + ":" + (n + 1);
                String label = "Lớp %02d".formatted(sequence.merge(c.getCourseCode() + ":" + c.getCohort(), 1, Integer::sum));
                if (c.getTheoryPeriods() > 0) result.add(new Unit(c, parent + ":LT", parent, label, "Cả lớp", c.getClassSize(), false, c.getTheoryPeriods()));
                if (c.getPracticePeriods() > 0) {
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

   
    static String requiredRoomType(CourseDemandDTO course, boolean practice) {
        String required = practice ? course.getPracticeRoomType() : course.getTheoryRoomType();
        return required == null || required.isBlank() ? null : required.trim();
    }
 /** Môn có yêu cầu loại phòng thì phòng phải đúng loại đó; không thì chỉ cần đúng nhóm phòng LT/TH. */
    static boolean roomFits(CourseDemandDTO course, boolean practice, Room room) {
        String required = requiredRoomType(course, practice);
        if (required != null) return required.equalsIgnoreCase(room.getRoomType());
        return room.isLab() == practice;
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

    private boolean spendProbe(int ceiling) {
        if (probes >= probeLimit || probes >= ceiling) return false;
        probes++;
        return true;
    }

    private void reserve(Unit unit, TeacherCandidateDTO teacher, Session s) {
        occupied.add(new Reservation(
                teacher.getTeacherId(),
                request.getTargetSchool().getSchoolId(),
                s.room().getRoomCode(),
                s.date(),
                periods.get(s.start()).getStart(),
                periods.get(s.start() + s.count() - 1).getEnd(),
                unit.parent(),
                unit.id(),
                unit.course().getCourseCode(),
                s.count(),
                unit.practice(),
                true
        ));
    }

    private void loadReservations() {
        for (var teacher : teachers.findAllWithFullSchedule()) {
            for (var school : teacher.getSchools()) {
                var hours = school.getCourses().isEmpty() ? Map.<Integer, PeriodTimeDTO>of() : ScheduleValidation.periods(school.getPeriods().stream()
                        .map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList());
                for (var c : school.getCourses()) {
                    if (request.getTargetSchool().getSchoolId().equals(school.getSchoolCode()) && "WEEKLY_AUTO".equals(c.getSourceType())) continue;
                    if (!ScheduleValidation.hasPeriods(hours, c.getStartPeriod(), c.getNumPeriods())) throw new IllegalArgumentException("Lịch hiện hữu thiếu bảng tiết.");
                    ScheduleValidation.monday(teacher.getSemesterStartDate());
                    if (c.getWeekSchedule() == null) throw new IllegalArgumentException("Lịch hiện hữu thiếu chuỗi tuần.");
                    boolean isPractice = "PRACTICE".equals(c.getSessionType()) || (c.getCourseName() != null && c.getCourseName().startsWith("[TH]"));
                    for (int w = 0; w < c.getWeekSchedule().length(); w++) if (c.getWeekSchedule().charAt(w) != '-' && c.getWeekSchedule().charAt(w) != ' ') {
                        occupied.add(new Reservation(
                                teacher.getTeacherId(),
                                school.getSchoolCode(),
                                c.getClassroom(),
                                teacher.getSemesterStartDate().plusDays(w * 7L + c.getDayOfWeek() - 2),
                                c.getCustomStartTime() != null ? c.getCustomStartTime() : hours.get(c.getStartPeriod()).getStart(),
                                c.getCustomEndTime() != null ? c.getCustomEndTime() : hours.get(c.getStartPeriod() + c.getNumPeriods() - 1).getEnd(),
                                c.getScheduleParentId(),
                                c.getScheduleClassId(),
                                c.getCourseCode(),
                                c.getNumPeriods(),
                                isPractice,
                                false
                        ));
                    }
                }
            }
        }
    }

    private int sum(List<Session> sessions) { return sessions.stream().mapToInt(Session::count).sum(); }

    private void record(Unit unit, TeacherCandidateDTO teacher, Session s, List<GeneratedClassSlot> slots,
                        Map<String, List<TeachingCourseDTO>> byTeacher, String extraPhaseNote) {
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
            mergedSlots.get(key).setPhaseNote(extraPhaseNote != null ? extraPhaseNote : "Các tuần có cùng thứ, tiết và phòng");
            mergedCourses.get(key).setWeekSchedule(combined);
            return;
        }
        String display = (unit.practice() ? "[TH] " : "[LT] ") + unit.course().getCourseName() + " (Khóa "
                + unit.course().getCohort() + " - " + unit.label() + ")";
        String snapshot;
        try { snapshot = json.writeValueAsString(unit.course()); }
        catch (Exception e) { throw new IllegalStateException("Không thể lưu dữ liệu kế hoạch.", e); }
        String note = extraPhaseNote != null ? "Ngày " + s.date() + " · " + extraPhaseNote : "Ngày " + s.date();
        slots.add(GeneratedClassSlot.builder().classId(unit.id()).parentClassId(unit.parent())
                .studyStartDate(unit.course().getStudyStartDate()).studyEndDate(unit.course().getStudyEndDate())
                .baseCourseName(unit.course().getCourseName()).credits(unit.course().getCredits()).courseCode(unit.course().getCourseCode())
                .courseName(display).cohort(unit.course().getCohort()).classGroup(unit.label()).subGroup(unit.sub()).sessionType(unit.type())
                .teacherId(null).teacherName(null).studentCount(unit.size())
                .room(s.room().getRoomCode()).roomType(s.room().getRoomType()).roomCapacity(s.room().getCapacity())
                .dayOfWeek(s.date().getDayOfWeek().getValue() + 1).startPeriod(s.start()).numPeriods(s.count()).weekSchedule(weeks)
                .phaseNote(note).build());
        byTeacher.computeIfAbsent(teacher.getTeacherId(), ignored -> new ArrayList<>()).add(TeachingCourseDTO.builder()
                .courseCode(unit.course().getCourseCode()).courseName(display).room(s.room().getRoomCode())
                .dayOfWeek(s.date().getDayOfWeek().getValue() + 1).startPeriod(s.start()).numPeriods(s.count()).weekSchedule(weeks)
                .sourceType("WEEKLY_AUTO").workflowStatus("DRAFT").scheduleClassId(unit.id()).scheduleParentId(unit.parent()).sessionType(unit.type()).planSnapshot(snapshot).build());
        mergedSlots.put(key, slots.getLast());
        mergedCourses.put(key, byTeacher.get(teacher.getTeacherId()).getLast());
    }
}
