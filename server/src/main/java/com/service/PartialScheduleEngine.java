package com.service;

import com.dto.*;
import com.model.*;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One proposal searches with retained reservations; nothing is persisted here. */
@Component @RequiredArgsConstructor
class PartialScheduleEngine {
    private final BoardReservationRepository reservations;
    private final InstitutionLecturerRepository lecturers;
    private final PublishedBusyReservations busy;
    record Outcome(boolean success, List<SchedulingEntry> rows, String message) {}

    Outcome arrange(SchedulingPlan plan, SchoolWideScheduleRequest request, AutoScheduleResponse result, List<SchedulingEntry> original,
                    Set<String> classes, Set<Long> ids, List<Room> rooms, LocalDate from, LocalDate to, Function<String, String> keyFactory) {
        var selected = original.stream().filter(e -> !e.isLocked() && (classes.contains(e.getScheduleClassId()) || ids.contains(e.getId()))).toList();
        var kept = original.stream().filter(e -> e.isLocked() || !classes.contains(e.getScheduleClassId()) && !ids.contains(e.getId())).toList();
        var byClass = result.getClassWorkloads().stream().collect(Collectors.toMap(ClassWorkloadDTO::getClassId, Function.identity()));
        var jobs = new ArrayList<SchedulingEntry>(); selected.forEach(e -> { var copy = new SchedulingEntry(); org.springframework.beans.BeanUtils.copyProperties(e, copy); jobs.add(copy); });
        var actors = new HashMap<String, InstitutionLecturer>();
        result.getCatalogContext().lecturers().forEach(a -> actors.put(a.teacherId(), lecturers.getReferenceById(a.lecturerId())));
        long temporary = -1;
        for (var classId : classes) {
            var w = byClass.get(classId); int placed = original.stream().filter(e -> classId.equals(e.getScheduleClassId())).mapToInt(SchedulingEntry::getNumPeriods).sum();
            int missing = w.getRequiredPeriods() - placed;
            if (missing < 0) return new Outcome(false, original, "Lớp/nhóm đã vượt nhu cầu; sửa số tiết trước khi xếp lại.");
            var course = request.getCourses().get(w.getDemandIndex() - 1); var actor = actors.get(w.getTeacherId());
            if (course.getMaxPeriodsPerSession() < 1 || course.getMaxPeriodsPerWeek() < 1) throw new IllegalArgumentException("Giới hạn tiết của nhu cầu không hợp lệ.");
            if (actor == null) return new Outcome(false, original, "Chọn giảng viên cho lớp/nhóm trước khi xếp lại.");
            int sequence = 0;
            while (missing > 0) {
                int count = Math.min(missing, course.getMaxPeriodsPerSession()); var e = new SchedulingEntry(); e.setId(temporary--);
                e.setEntryKey(keyFactory.apply(classId + ":missing:" + sequence++)); e.setScheduleClassId(classId); e.setScheduleParentId(w.getParentClassId());
                var demand = new SchedulingDemand(); demand.setDemandIndex(w.getDemandIndex()); demand.setCourseCode(w.getCourseCode()); demand.setCohort(w.getCohort()); e.setDemand(demand);
                e.setClassGroup(w.getClassGroup()); e.setSessionType(w.getSessionType()); e.setStudentCount(w.getStudentCount()); e.setLecturer(actor);
                e.setLecturerName(course.getTeachers().stream().filter(t -> w.getTeacherId().equals(t.getTeacherId())).map(TeacherCandidateDTO::getTeacherName).findFirst().orElse(actor.getFullName()));
                e.setCourseName(CatalogService.text(("PRACTICE".equals(w.getSessionType()) ? "[TH] " : "[LT] ") + course.getCourseName() + " (Khóa " + course.getCohort() + " - " + w.getClassGroup() + ")", 255, "Tên buổi"));
                e.setNumPeriods(count); jobs.add(e); missing -= count;
            }
        }
        if (jobs.isEmpty()) return new Outcome(false, original, "Phạm vi không có buổi cần xếp lại hoặc chỉ có buổi khóa.");
        if (jobs.size() > 2000) throw new IllegalArgumentException("Tối đa 2000 buổi trong một lần xếp lại.");
        jobs.sort(Comparator.comparing((SchedulingEntry e) -> "PRACTICE".equals(e.getSessionType())).thenComparing(e -> request.getCourses().get(e.getDemand().getDemandIndex() - 1).getStudyEndDate()).thenComparing(SchedulingEntry::getScheduleClassId).thenComparing(SchedulingEntry::getEntryKey));
        var keys = actorKeys(result); var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        var available = rooms.stream().sorted(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getRoomCode)).toList();
        int[] probes = {0};
        for (int strategy = 0; strategy < 2 && probes[0] < 250000; strategy++) {
            var occupied = external(plan, request, keys);
            kept.forEach(e -> reserve(occupied, plan, keys, e));
            var counts = new HashMap<String, Integer>(); kept.forEach(e -> counts.merge(e.getScheduleClassId() + ":" + week(request, e.getLessonDate()), e.getNumPeriods(), Integer::sum));
            var output = new ArrayList<SchedulingEntry>(kept); boolean solved = true;
            for (var job : jobs) {
                var course = request.getCourses().get(job.getDemand().getDemandIndex() - 1); var dates = days(request, course, "PRACTICE".equals(job.getSessionType()), from, to);
                if (job.getNumPeriods() > course.getMaxPeriodsPerSession()) { solved = false; break; }
                if (strategy == 1) Collections.reverse(dates);
                String teacher = keys.get(job.getLecturer().getId()), school = plan.getInstitution().getSchoolCode(); boolean practice = "PRACTICE".equals(job.getSessionType());
                SchedulingEntry placed = null;
                search: for (var date : dates) {
                    if (++probes[0] > 250000) break;
                    String countKey = job.getScheduleClassId() + ":" + week(request, date);
                    if (counts.getOrDefault(countKey, 0) + job.getNumPeriods() > course.getMaxPeriodsPerWeek() || occupied.differentSessionDayBlocked(teacher, date, practice)) continue;
                    for (var start : new TreeSet<>(periods.keySet())) {
                        if (++probes[0] > 250000) break search;
                        if (!ScheduleValidation.hasPeriods(periods, start, job.getNumPeriods())) continue;
                        boolean contiguous = true;
                        for (int p = start + 1; p < start + job.getNumPeriods(); p++) if (Duration.between(periods.get(p-1).getEnd(), periods.get(p).getStart()).toMinutes() > 20) contiguous = false;
                        if (!contiguous) continue;
                        var begin = periods.get(start).getStart(); var end = periods.get(start + job.getNumPeriods() - 1).getEnd();
                        if (occupied.teacherBusy(teacher, date, begin, end) || occupied.studentsBusy(school, job.getScheduleParentId(), job.getScheduleClassId(), practice, date, begin, end)) continue;
                        for (var room : available) {
                            if (++probes[0] > 250000) break search;
                            if (room.isLab() != practice || room.getCapacity() < job.getStudentCount() || occupied.roomBusy(school, room.getRoomCode(), date, begin, end)) continue;
                            placed = new SchedulingEntry(); org.springframework.beans.BeanUtils.copyProperties(job, placed);
                            placed.setLessonDate(date); placed.setRoom(room); placed.setRoomCode(room.getRoomCode()); placed.setStartPeriod(start); placed.setStartTime(begin); placed.setEndTime(end);
                            counts.merge(countKey, placed.getNumPeriods(), Integer::sum); break search;
                        }
                    }
                }
                if (placed == null) { solved = false; break; }
                reserve(occupied, plan, keys, placed); output.add(placed);
            }
            if (solved) return new Outcome(true, output, "Đã tìm được phương án trong phạm vi đã chọn; các buổi còn lại được giữ.");
        }
        return new Outcome(false, original, "Chưa tìm được phương án trong phạm vi và giới hạn tìm kiếm. Bản nháp cũ được giữ; thử đổi khoảng ngày, phòng hoặc mở rộng phạm vi.");
    }

    private Map<Long, String> actorKeys(AutoScheduleResponse result) {
        var output = new HashMap<Long, String>(); var current = new HashMap<Long, InstitutionLecturer>();
        lecturers.findAllById(result.getCatalogContext().lecturers().stream().map(com.dto.CatalogSchedulingDTO.LecturerIdentity::lecturerId).toList()).forEach(l -> current.put(l.getId(), l));
        for (var actor : result.getCatalogContext().lecturers()) {
            String key = actor.teacherId(); var profile = current.get(actor.lecturerId());
            if (key.startsWith("DRAFT_") && profile != null) { if (profile.getAccount() != null) key = profile.getAccount().getTeacherId(); else if (profile.getScheduleTeacher() != null) key = profile.getScheduleTeacher().getTeacherId(); }
            output.put(actor.lecturerId(), key);
        }
        return output;
    }
    private WeeklyReservations external(SchedulingPlan plan, SchoolWideScheduleRequest request, Map<Long, String> keys) {
        var output = new WeeklyReservations(); var start = request.getSemesterStartDate(); var end = start.plusWeeks(100).minusDays(1);
        for (var c : reservations.findRelevant(plan.getId(), plan.getInstitution().getSchoolCode().toUpperCase(Locale.ROOT), keys.values(), start, end)) {
            var school = c.getSchool(); var teacher = school.getTeacher(); ScheduleValidation.monday(teacher.getSemesterStartDate());
            if (c.getWeekSchedule() == null || c.getDayOfWeek() < 2 || c.getDayOfWeek() > 8) throw new IllegalArgumentException("Lịch hiện hành thiếu ngày/tuần; cần đối chiếu trước.");
            var hours = ScheduleValidation.periods(school.getPeriods().stream().map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList());
            if (!ScheduleValidation.hasPeriods(hours, c.getStartPeriod(), c.getNumPeriods())) throw new IllegalArgumentException("Lịch hiện hành thiếu giờ tiết; cần đối chiếu trước.");
            var begin = c.getCustomStartTime() == null ? hours.get(c.getStartPeriod()).getStart() : c.getCustomStartTime(); var finish = c.getCustomEndTime() == null ? hours.get(c.getStartPeriod() + c.getNumPeriods() - 1).getEnd() : c.getCustomEndTime();
            for (int w = 0; w < c.getWeekSchedule().length(); w++) if ("- ".indexOf(c.getWeekSchedule().charAt(w)) < 0) {
                var day = teacher.getSemesterStartDate().plusDays(w * 7L + c.getDayOfWeek() - 2);
                if (!day.isBefore(start) && !day.isAfter(end)) output.add(new WeeklyReservations.Reservation(teacher.getTeacherId(), school.getSchoolCode(), c.getClassroom(), day, begin, finish, c.getScheduleParentId(), c.getScheduleClassId(), "PRACTICE".equals(c.getSessionType()) || c.getCourseName().startsWith("[TH]"), false));
            }
        }
        busy.reservations(plan.getId(), start, end).forEach(output::add);
        PublishedBusyReservations.own(request).forEach(b -> { if (keys.containsKey(b.lecturerId())) output.add(PublishedBusyReservations.busy(keys.get(b.lecturerId()), b.date(), b.startTime(), b.endTime())); });
        return output;
    }
    private static void reserve(WeeklyReservations target, SchedulingPlan plan, Map<Long, String> keys, SchedulingEntry e) {
        target.add(new WeeklyReservations.Reservation(keys.get(e.getLecturer().getId()), plan.getInstitution().getSchoolCode(), e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getScheduleParentId(), e.getScheduleClassId(), "PRACTICE".equals(e.getSessionType()), true));
    }
    private static int week(SchoolWideScheduleRequest r, LocalDate day) { return (int) ChronoUnit.DAYS.between(r.getSemesterStartDate(), day) / 7; }
    private static List<LocalDate> days(SchoolWideScheduleRequest r, CourseDemandDTO c, boolean practice, LocalDate from, LocalDate to) {
        var first = c.getStudyStartDate(); var last = c.getStudyEndDate();
        if (practice) {
            if (c.getPracticeStartDate() != null) { first = c.getPracticeStartDate(); last = c.getPracticeEndDate(); }
            else { long span = ChronoUnit.DAYS.between(first, last) + 1; if ("MIDDLE".equals(c.getPracticeMode())) { first = first.plusDays(span / 3); last = c.getStudyStartDate().plusDays(2 * span / 3); } if ("END".equals(c.getPracticeMode())) first = first.plusDays(2 * span / 3); }
        }
        int phase = (int) ChronoUnit.WEEKS.between(r.getSemesterStartDate(), first); var output = new ArrayList<LocalDate>();
        for (var day = first.isBefore(from) ? from : first; !day.isAfter(last) && !day.isAfter(to); day = day.plusDays(1))
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY && (!practice || !"ALTERNATE".equals(c.getPracticeMode()) || (week(r, day) - phase) % 2 == 0)) output.add(day);
        return output;
    }
}
