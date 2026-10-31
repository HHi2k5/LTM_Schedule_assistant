package com.service;

import com.dto.*;
import com.dto.SchedulingPlanDTO.Conflict;
import com.model.*;
import com.repository.TeacherRepository;
import com.service.WeeklyReservations.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
class SchedulingPlanValidation {
    private final CatalogSchedulingService catalog;
    private final TeacherRepository teachers;
    private final PublishedBusyReservations publishedBusy;

    List<Conflict> check(SchedulingPlan plan, SchedulingPlanVersion version, SchoolWideScheduleRequest request,
                         AutoScheduleResponse result, List<SchedulingEntry> entries) throws Exception {
        var errors = new ArrayList<Conflict>();
        if (!version.isComplete() || !result.isComplete() || entries.isEmpty())
            errors.add(general("WORKLOAD", "Bản nháp chưa xếp đủ khối lượng hoặc không có buổi học."));
        var totals = new HashMap<String, Long>(); entries.forEach(e -> totals.merge(e.getScheduleClassId(), (long) e.getNumPeriods(), Long::sum));
        if (result.getClassWorkloads() == null) errors.add(general("WORKLOAD", "Bản nháp thiếu dữ liệu khối lượng."));
        else for (var workload : result.getClassWorkloads()) {
            if (errors.size() >= 200) break;
            if (totals.getOrDefault(workload.getClassId(), 0L) != workload.getRequiredPeriods())
                errors.add(general("WORKLOAD", workload.getCourseCode() + " / " + workload.getClassGroup() + ": số tiết chưa khớp nhu cầu."));
        }
        com.dto.CatalogSchedulingDTO.Options options;
        try { options = catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId()); }
        catch (IllegalArgumentException e) { errors.add(general("CONFIGURATION", e.getMessage())); return errors; }
        var profiles = new HashMap<Long, com.dto.CatalogSchedulingDTO.LecturerOption>(); options.lecturers().forEach(o -> profiles.put(o.lecturer().id(), o));
        var source = new HashMap<Long, com.dto.CatalogSchedulingDTO.LecturerIdentity>();
        if (result.getCatalogContext() != null) result.getCatalogContext().lecturers().forEach(i -> source.put(i.lecturerId(), i));
        var identities = new HashMap<Long, String>();
        var actors = new LinkedHashMap<Long, SchedulingEntry>(); entries.forEach(e -> actors.putIfAbsent(e.getLecturer().getId(), e));
        PublishedBusyReservations.own(request).forEach(b -> actors.putIfAbsent(b.lecturerId(), null));
        for (var actor : actors.entrySet()) {
            if (errors.size() >= 200) break;
            Long id = actor.getKey(); var entry = actor.getValue();
            var current = profiles.get(id); var original = source.get(id);
            if (current == null || !current.eligible() || original == null) {
                errors.add(entry == null ? general("LECTURER", "GV có lịch bận không còn đủ điều kiện trong khoa.") : issue(entry, "LECTURER", "Giảng viên không còn đủ điều kiện hoặc không thuộc khoa của bản lịch.")); continue;
            }
            var profile = current.lecturer();
            String actual = profile.accountTeacherId() != null ? profile.accountTeacherId() : profile.scheduleTeacherId() != null ? profile.scheduleTeacherId() : original.teacherId();
            if (!LecturerAccountService.compatible(original, profile)
                    || (!original.teacherId().startsWith("DRAFT_") && !original.teacherId().equals(actual))) {
                errors.add(entry == null ? general("IDENTITY", "Danh tính GV có lịch bận đã đổi.") : issue(entry, "IDENTITY", "Liên kết tài khoản/hồ sơ giảng viên đã đổi; cần tạo lại bản nháp để đối chiếu.")); continue;
            }
            identities.put(id, actual);
        }
        var occupied = new WeeklyReservations();
        var busy = new WeeklyReservations();
        var ownBusy = new WeeklyReservations();
        publishedBusy.reservations(plan.getId(), plan.getSemesterStartDate(), plan.getSemesterStartDate().plusWeeks(100).minusDays(1)).forEach(busy::add);
        for (var row : PublishedBusyReservations.own(request)) {
            if (!identities.containsKey(row.lecturerId()) || row.date() == null || row.startTime() == null || row.endTime() == null || !row.startTime().isBefore(row.endTime())) {
                errors.add(general("BUSY_CONFIGURATION", "Lịch bận thiếu danh tính hoặc ngày/giờ hợp lệ.")); continue;
            }
            var reservation = PublishedBusyReservations.busy(identities.get(row.lecturerId()), row.date(), row.startTime(), row.endTime()); busy.add(reservation); ownBusy.add(reservation);
        }
        for (var teacher : teachers.findAllWithFullSchedule()) {
            boolean usesTeacher = identities.containsValue(teacher.getTeacherId());
            boolean retained = teacher.getSchools().stream().flatMap(s -> s.getCourses().stream()).anyMatch(c -> !plan.getId().equals(c.getSchedulingPlanId()));
            if (usesTeacher && retained && !plan.getSemesterStartDate().equals(teacher.getSemesterStartDate()))
                errors.add(general("SEMESTER_ANCHOR", "Mốc tuần của giảng viên khác lịch được giữ lại; cần đối chiếu trước."));
            for (var school : teacher.getSchools()) {
                var kept = school.getCourses().stream().filter(c -> !plan.getId().equals(c.getSchedulingPlanId())).toList();
                if (kept.isEmpty()) continue;
                List<PeriodTimeDTO> periods = school.getPeriods().stream().sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber)).map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList();
                if (usesTeacher && school.getSchoolCode().equalsIgnoreCase(plan.getInstitution().getSchoolCode()) && !periods.equals(request.getTargetSchool().getPeriods()))
                    errors.add(general("PERIOD_CONFIGURATION", "Bảng giờ khác lịch khoa khác/lịch cá nhân được giữ lại của cùng giảng viên."));
                Map<Integer, PeriodTimeDTO> hours;
                try { hours = ScheduleValidation.periods(periods); }
                catch (IllegalArgumentException e) { errors.add(general("EXISTING_DATA", "Lịch hiện hành thiếu/sai bảng giờ; cần đối chiếu trước.")); continue; }
                for (var course : kept) {
                    if (!ScheduleValidation.hasPeriods(hours, course.getStartPeriod(), course.getNumPeriods()) || course.getWeekSchedule() == null) {
                        errors.add(general("EXISTING_DATA", "Lịch hiện hành có buổi không quy đổi được thời gian.")); continue;
                    }
                    var begin = course.getCustomStartTime() == null ? hours.get(course.getStartPeriod()).getStart() : course.getCustomStartTime();
                    var end = course.getCustomEndTime() == null ? hours.get(course.getStartPeriod() + course.getNumPeriods() - 1).getEnd() : course.getCustomEndTime();
                    for (int week = 0; week < course.getWeekSchedule().length(); week++) {
                        char marker = course.getWeekSchedule().charAt(week); if (marker == '-' || marker == ' ') continue;
                        var lessonDate = teacher.getSemesterStartDate().plusDays(week * 7L + course.getDayOfWeek() - 2);
                        if (errors.size() < 200 && course.getSchedulingPlanId() != null && ownBusy.teacherBusy(teacher.getTeacherId(), lessonDate, begin, end))
                            errors.add(new Conflict("BUSY_PUBLISHED_CONFLICT", "BLOCKING", course.getSchedulingEntryId() == null ? List.of() : List.of(course.getSchedulingEntryId()), lessonDate, begin, end, teacher.getTeacherId() + " / " + school.getSchoolCode(), "Khoảng bận mới giao với buổi đang áp dụng của bản lịch khác; cần đối chiếu trước khi xác nhận."));
                        occupied.add(new Reservation(teacher.getTeacherId(), school.getSchoolCode().toUpperCase(Locale.ROOT), course.getClassroom(),
                                lessonDate, begin, end,
                                course.getScheduleParentId(), course.getScheduleClassId(), "PRACTICE".equals(course.getSessionType()) || course.getCourseName().startsWith("[TH]"), false));
                    }
                }
            }
        }
        var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        String school = plan.getInstitution().getSchoolCode().toUpperCase(Locale.ROOT);
        for (var entry : entries) {
            if (errors.size() >= 200) break;
            String teacher = identities.get(entry.getLecturer().getId()); if (teacher == null) continue;
            boolean practice = "PRACTICE".equals(entry.getSessionType());
            var room = entry.getRoom();
            if (!room.isActive() || !room.getSchoolCode().equalsIgnoreCase(school) || room.isLab() != practice || room.getCapacity() < entry.getStudentCount())
                errors.add(issue(entry, "ROOM_CONFIGURATION", "Phòng đã ngừng sử dụng, khác loại hoặc không còn đủ sức chứa."));
            var demand = request.getCourses().get(entry.getDemand().getDemandIndex() - 1);
            boolean assignment = options.assignments().stream().anyMatch(a -> a.lecturerId().equals(entry.getLecturer().getId())
                    && a.courseCode().equalsIgnoreCase(demand.getCourseCode()) && a.cohort().equalsIgnoreCase(demand.getCohort()));
            if (!assignment) errors.add(issue(entry, "ASSIGNMENT", "Phân công GV–môn–khóa đã bị ngừng sử dụng hoặc thay đổi."));
            if (entry.getLessonDate().isBefore(demand.getStudyStartDate()) || entry.getLessonDate().isAfter(demand.getStudyEndDate())
                    || !ScheduleValidation.hasPeriods(periods, entry.getStartPeriod(), entry.getNumPeriods())
                    || !periods.get(entry.getStartPeriod()).getStart().equals(entry.getStartTime())
                    || !periods.get(entry.getStartPeriod() + entry.getNumPeriods() - 1).getEnd().equals(entry.getEndTime()))
                errors.add(issue(entry, "TIME_CONFIGURATION", "Ngày/giờ buổi học không khớp kế hoạch và bảng tiết đã lưu."));
            if (occupied.teacherBusy(teacher, entry.getLessonDate(), entry.getStartTime(), entry.getEndTime())) errors.add(issue(entry, "TEACHER_CONFLICT", "Giảng viên trùng lịch hiện hành hoặc buổi khác trong nháp."));
            if (busy.teacherBusy(teacher, entry.getLessonDate(), entry.getStartTime(), entry.getEndTime())) errors.add(issue(entry, "BUSY_CONFLICT", "Buổi dạy trùng lịch bận của giảng viên."));
            if (occupied.roomBusy(school, entry.getRoomCode(), entry.getLessonDate(), entry.getStartTime(), entry.getEndTime())) errors.add(issue(entry, "ROOM_CONFLICT", "Phòng đã được dùng bởi lịch hiện hành hoặc buổi khác trong nháp."));
            if (occupied.studentsBusy(school, entry.getScheduleParentId(), entry.getScheduleClassId(), practice, entry.getLessonDate(), entry.getStartTime(), entry.getEndTime())) errors.add(issue(entry, "CLASS_CONFLICT", "Lớp/nhóm trùng buổi học."));
            if (occupied.differentSessionDayBlocked(teacher, entry.getLessonDate(), practice)) errors.add(issue(entry, "SESSION_DAY", "Ngày LT/TH của giảng viên chưa tách theo quy tắc xếp lịch."));
            occupied.add(new Reservation(teacher, school, entry.getRoomCode(), entry.getLessonDate(), entry.getStartTime(), entry.getEndTime(), entry.getScheduleParentId(), entry.getScheduleClassId(), practice, true));
        }
        return errors;
    }

    private static Conflict general(String type, String message) { return new Conflict(type, "BLOCKING", List.of(), null, null, null, null, message); }
    private static Conflict issue(SchedulingEntry e, String type, String message) {
        return new Conflict(type, "BLOCKING", List.of(e.getId()), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getLecturer().getLecturerCode() + " / " + e.getRoomCode(), message);
    }
}
