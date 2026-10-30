package com.service;

import com.dto.*;
import com.model.*;
import com.repository.RoomRepository;
import com.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AutoScheduleService {
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final ScheduleService scheduleService;
    private final ScheduleWriteLock writeLock;
    private static final int[] DEFAULT_START_PERIODS = {1, 4, 7, 10};

    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse generateSchoolWideSchedule(SchoolWideScheduleRequest request) {
        validate(request);
        writeLock.acquire();
        return generateScheduleWithRooms(request, roomRepository.findBySchoolCodeOrderByCapacityAsc(request.getTargetSchool().getSchoolId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse generateScheduleWithRooms(SchoolWideScheduleRequest request, List<Room> availableRooms) {
        if (request != null && request.getCourses() != null && request.getCourses().stream().anyMatch(c -> c != null
                && (c.getStudyStartDate() != null || c.getStudyEndDate() != null || c.getTheoryPeriods() != null || c.getPracticePeriods() != null))) {
            writeLock.acquire();
            return new WeeklyScheduleEngine(teacherRepository, scheduleService).generate(request, availableRooms);
        }
        validate(request);
        writeLock.acquire();
        var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        String schoolId = request.getTargetSchool().getSchoolId();
        var rooms = availableRooms.stream().filter(r -> schoolId.equals(r.getSchoolCode()))
                .sorted(Comparator.comparingInt(Room::getCapacity)).toList();
        var replacedTeachers = new HashSet<String>();
        for (var course : request.getCourses()) {
            var quota = allocateClassesToTeachers(course.getTotalClassesToOpen(), course.getTeachers());
            for (var teacher : course.getTeachers()) if (quota.get(teacher.getTeacherId()) > 0) replacedTeachers.add(teacher.getTeacherId());
        }
        var occupied = existingReservations(request, replacedTeachers);
        var result = new ArrayList<GeneratedClassSlot>();
        var warnings = new ArrayList<String>();
        var byTeacher = new LinkedHashMap<String, List<TeachingCourseDTO>>();
        var names = new HashMap<String, String>();
        var sequence = new HashMap<String, Integer>();
        var preferred = new HashMap<String, Set<Integer>>();
        var theoryDaysByTeacher = new HashMap<String, Set<Integer>>();
        var practicePhases = new HashMap<String, Integer>();
        var pendingPractice = new ArrayList<PracticeDemand>();
        for (var course : request.getCourses()) {
            var quota = allocateClassesToTeachers(course.getTotalClassesToOpen(), course.getTeachers());
            int size = course.getClassSize() > 0 ? course.getClassSize() : 50;
            int count = course.getNumPeriodsPerSession() > 0 ? course.getNumPeriodsPerSession() : 3;
            int startWeek = course.getStartWeek() > 0 ? course.getStartWeek() : 1;
            String theoryWeeks = weeks(startWeek, request.getTotalWeeks());
            String cohort = course.getCohort() == null || course.getCohort().isBlank() ? "2024" : course.getCohort();
            for (var teacher : course.getTeachers()) {
                String id = teacher.getTeacherId();
                var days = preferred.computeIfAbsent(id + ":" + course.getCourseCode(), ignored -> new LinkedHashSet<>());
                for (int n = 0; n < quota.get(id); n++) {
                    String group = String.format("Lớp %02d", sequence.merge(course.getCourseCode(), 1, Integer::sum));
                    var theory = placement(request, rooms, periods, occupied, teacher, size, false, course.getFixedDayOfWeek(),
                            course.getFixedStartPeriod(), count, days, theoryWeeks, Set.of());
                    if (theory == null) {
                        warnings.add("Không có phòng/giờ hợp lệ cho " + course.getCourseCode() + " - " + group + " - " + id);
                        continue;
                    }
                    names.put(id, teacher.getTeacherName());
                    var courses = byTeacher.computeIfAbsent(id, ignored -> new ArrayList<>());
                    recordClassSlot(result, courses, teacher, course, cohort, group, "Cả lớp", "THEORY", size,
                            theory.room(), theory.day(), theory.start(), count, theoryWeeks, "Lý thuyết cả lớp", rooms.size());
                    book(occupied, request, id, theory, count, theoryWeeks);
                    days.add(theory.day());
                    theoryDaysByTeacher.computeIfAbsent(id, ignored -> new HashSet<>()).add(theory.day());
                    if (!Boolean.FALSE.equals(course.getHasPractice())) {
                        int firstPracticeWeek = startWeek + 1;
                        int phase = practicePhases.getOrDefault(id, firstPracticeWeek % 2);
                        if (firstPracticeWeek % 2 != phase) firstPracticeWeek++;
                        practicePhases.put(id, 1 - phase);
                        pendingPractice.add(new PracticeDemand(teacher, course, cohort, group, size, count,
                                firstPracticeWeek, days));
                    }
                }
            }
        }
        // Reserve every theory class before placing the two alternating practice lessons.
        for (var demand : pendingPractice) {
            var teacher = demand.teacher();
            int groups = Math.min(demand.size(), Math.max(2, (int) Math.ceil(demand.size() / 30.0)));
            for (int sub = 1; sub <= groups; sub++) {
                int subSize = demand.size() / groups + (sub <= demand.size() % groups ? 1 : 0);
                Placement practice = null;
                String practiceWeeks = null;
                int firstWeek = demand.firstWeek();
                // Keep the class's alternating-week phase if its first pair is occupied.
                for (; firstWeek + 2 <= request.getTotalWeeks(); firstWeek += 2) {
                    practiceWeeks = practiceWeeks(firstWeek, request.getTotalWeeks());
                    practice = placement(request, rooms, periods, occupied, teacher, subSize, true, null, null,
                            demand.count(), demand.days(), practiceWeeks, theoryDaysByTeacher.get(teacher.getTeacherId()));
                    if (practice != null) break;
                }
                if (practice == null) {
                    warnings.add("Không đủ 2 buổi thực hành cách tuần vào ngày khác lý thuyết của giảng viên hoặc thiếu phòng LAB/giờ hợp lệ cho "
                            + demand.course().getCourseCode() + " - " + demand.group() + " - Tổ " + sub);
                    continue;
                }
                recordClassSlot(result, byTeacher.get(teacher.getTeacherId()), teacher, demand.course(), demand.cohort(),
                        demand.group() + " - Tổ " + sub, "Tổ " + sub, "PRACTICE", subSize, practice.room(),
                        practice.day(), practice.start(), demand.count(), practiceWeeks,
                        "Bài 1: tuần " + firstWeek + "; Bài 2: tuần " + (firstWeek + 2) + " (cách tuần)", rooms.size());
                book(occupied, request, teacher.getTeacherId(), practice, demand.count(), practiceWeeks);
                demand.days().add(practice.day());
            }
        }
        // A partial plan is a preview only and never overwrites existing unscheduled classes.
        boolean saved = warnings.isEmpty() && !result.isEmpty();
        if (saved) for (var entry : byTeacher.entrySet()) {
            scheduleService.saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(entry.getKey())
                    .teacherName(names.get(entry.getKey())).semesterStartDate(request.getSemesterStartDate())
                    .school(request.getTargetSchool()).courses(entry.getValue()).build());
        }
        return AutoScheduleResponse.builder().schoolId(schoolId).schoolName(request.getTargetSchool().getSchoolName())
                .semesterStartDate(request.getSemesterStartDate()).periods(request.getTargetSchool().getPeriods())
                .status(saved ? "SUCCESS" : "NOT_SAVED").saved(saved)
                .message(saved ? "Đã xếp và lưu đầy đủ lịch toàn trường." : "Chưa lưu: không xếp được đầy đủ các lớp. Lịch cũ được giữ nguyên.")
                .totalRoomsAvailable(rooms.size()).totalClassesScheduled(result.size()).scheduledClasses(result).unscheduledWarnings(warnings).build();
    }

    private void validate(SchoolWideScheduleRequest request) {
        if (request == null || request.getTargetSchool() == null || request.getCourses() == null || request.getCourses().isEmpty())
            throw new IllegalArgumentException("Thiếu kế hoạch hoặc thông tin trường.");
        ScheduleValidation.monday(request.getSemesterStartDate());
        ScheduleValidation.text(request.getTargetSchool().getSchoolId(), 50, "Mã trường");
        ScheduleValidation.text(request.getTargetSchool().getSchoolName(), 255, "Tên trường");
        ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        if (request.getTotalWeeks() < 1 || request.getTotalWeeks() > 100 || request.getCourses().size() > 5000)
            throw new IllegalArgumentException("Số tuần phải từ 1 đến 100; tối đa 5000 dòng kế hoạch.");
        for (var course : request.getCourses()) {
            if (course == null || course.getTeachers() == null || course.getTeachers().isEmpty()
                    || course.getTotalClassesToOpen() < 0 || course.getTotalClassesToOpen() > 500
                    || course.getClassSize() < 0 || course.getClassSize() > 1000
                    || course.getNumPeriodsPerSession() < 0 || course.getNumPeriodsPerSession() > 30
                    || course.getStartWeek() < 0 || course.getStartWeek() > request.getTotalWeeks()
                    || (course.getFixedDayOfWeek() != null && (course.getFixedDayOfWeek() < 2 || course.getFixedDayOfWeek() > 8))
                    || (course.getFixedStartPeriod() != null && (course.getFixedStartPeriod() < 1 || course.getFixedStartPeriod() > 30)))
                throw new IllegalArgumentException("Dòng kế hoạch có số lớp, sĩ số, tuần, thứ hoặc tiết không hợp lệ.");
            ScheduleValidation.text(course.getCourseCode(), 50, "Mã môn");
            ScheduleValidation.text(course.getCourseName(), 180, "Tên môn");
            for (var teacher : course.getTeachers()) {
                if (teacher == null) throw new IllegalArgumentException("Thiếu giảng viên.");
                ScheduleValidation.text(teacher.getTeacherId(), 50, "Mã GV");
                ScheduleValidation.text(teacher.getTeacherName(), 100, "Tên GV");
            }
            allocateClassesToTeachers(course.getTotalClassesToOpen(), course.getTeachers());
        }
    }

    private Map<String, Integer> allocateClassesToTeachers(int total, List<TeacherCandidateDTO> teachers) {
        var allocation = new LinkedHashMap<String, Integer>();
        boolean explicit = teachers.stream().anyMatch(t -> t.getAssignedClasses() != null);
        int sum = 0;
        for (int i = 0; i < teachers.size(); i++) {
            var teacher = teachers.get(i);
            int quota = explicit ? (teacher.getAssignedClasses() == null ? 0 : teacher.getAssignedClasses()) : total / teachers.size() + (i < total % teachers.size() ? 1 : 0);
            if (quota < 0 || allocation.putIfAbsent(teacher.getTeacherId(), quota) != null)
                throw new IllegalArgumentException("Quota âm hoặc mã GV lặp trong cùng môn.");
            sum += quota;
        }
        if (sum != total) throw new IllegalArgumentException("Tổng quota giảng viên không bằng số lớp yêu cầu.");
        return allocation;
    }

    private record Placement(Room room, int day, int start) {}
    private record PracticeDemand(TeacherCandidateDTO teacher, CourseDemandDTO course, String cohort, String group,
            int size, int count, int firstWeek, Set<Integer> days) {}
    private record Reservation(String teacher, String school, String room, LocalDate date, LocalTime start, LocalTime end, boolean practice) {}

    private List<Reservation> existingReservations(SchoolWideScheduleRequest request, Set<String> replaced) {
        var reservations = new ArrayList<Reservation>();
        for (var teacher : teacherRepository.findAllWithFullSchedule()) {
            if (replaced.contains(teacher.getTeacherId()) && !teacher.getSchools().isEmpty()
                    && !request.getSemesterStartDate().equals(teacher.getSemesterStartDate()))
                throw new IllegalArgumentException("Mốc tuần của " + teacher.getTeacherId() + " khác lịch đã lưu; cần đối chiếu trước.");
            for (var school : teacher.getSchools()) {
                if (replaced.contains(teacher.getTeacherId()) && school.getSchoolCode().equals(request.getTargetSchool().getSchoolId())) continue;
                if (school.getCourses().isEmpty()) continue;
                var periods = ScheduleValidation.periods(school.getPeriods().stream()
                        .map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList());
                for (var course : school.getCourses()) {
                    if (!ScheduleValidation.hasPeriods(periods, course.getStartPeriod(), course.getNumPeriods()))
                        throw new IllegalArgumentException("Lịch hiện hữu thiếu bảng giờ; cần sửa trước khi xếp lịch.");
                    addReservations(reservations, teacher.getTeacherId(), school.getSchoolCode(), course.getClassroom(),
                            teacher.getSemesterStartDate(), course.getDayOfWeek(), course.getWeekSchedule(),
                            periods.get(course.getStartPeriod()).getStart(), periods.get(course.getStartPeriod() + course.getNumPeriods() - 1).getEnd(),
                            course.getCourseName() != null && course.getCourseName().startsWith("[TH]"));
                }
            }
        }
        return reservations;
    }

    private Placement placement(SchoolWideScheduleRequest request, List<Room> rooms, Map<Integer, PeriodTimeDTO> periods,
            List<Reservation> occupied, TeacherCandidateDTO teacher, int size, boolean lab, Integer fixedDay,
            Integer fixedStart, int count, Set<Integer> preferred, String weeks, Set<Integer> excludedDays) {
        var days = new LinkedHashSet<Integer>();
        if (fixedDay != null) days.add(fixedDay);
        days.addAll(preferred);
        for (int day = 2; day <= 7; day++) days.add(day);
        days.removeAll(excludedDays);
        var starts = new LinkedHashSet<Integer>();
        if (fixedStart != null) starts.add(fixedStart);
        for (int start : DEFAULT_START_PERIODS) starts.add(start);
        for (int day : days) for (int start : starts) {
            if (!ScheduleValidation.hasPeriods(periods, start, count)) continue;
            LocalTime begin = periods.get(start).getStart(), end = periods.get(start + count - 1).getEnd();
            for (var room : rooms) {
                if (room.isLab() != lab || room.getCapacity() < size) continue;
                boolean free = true;
                for (LocalDate date : dates(request.getSemesterStartDate(), day, weeks)) {
                    for (var busy : occupied) {
                        boolean sameTeacher = teacher.getTeacherId().equals(busy.teacher());
                        boolean overlappingHours = begin.isBefore(busy.end()) && busy.start().isBefore(end);
                        boolean sameRoom = request.getTargetSchool().getSchoolId().equals(busy.school()) && room.getRoomCode().equals(busy.room());
                        if (date.equals(busy.date()) && ((sameTeacher && lab != busy.practice())
                                || (overlappingHours && (sameTeacher || sameRoom)))) {
                            free = false; break;
                        }
                    }
                    if (!free) break;
                }
                if (free) return new Placement(room, day, start);
            }
        }
        return null;
    }

    private void book(List<Reservation> occupied, SchoolWideScheduleRequest request, String teacher, Placement placement, int count, String weeks) {
        var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods());
        addReservations(occupied, teacher, request.getTargetSchool().getSchoolId(), placement.room().getRoomCode(),
                request.getSemesterStartDate(), placement.day(), weeks, periods.get(placement.start()).getStart(),
                periods.get(placement.start() + count - 1).getEnd(), placement.room().isLab());
    }
    private void addReservations(List<Reservation> target, String teacher, String school, String room, LocalDate anchor,
            int day, String weeks, LocalTime start, LocalTime end, boolean practice) {
        for (var date : dates(anchor, day, weeks)) target.add(new Reservation(teacher, school, room, date, start, end, practice));
    }
    private List<LocalDate> dates(LocalDate anchor, int day, String weeks) {
        ScheduleValidation.monday(anchor);
        if (weeks == null || day < 2 || day > 8) throw new IllegalArgumentException("Lịch hiện hữu có thứ/tuần không hợp lệ.");
        var dates = new ArrayList<LocalDate>();
        for (int w = 0; w < weeks.length(); w++) if (weeks.charAt(w) != '-' && weeks.charAt(w) != ' ') dates.add(anchor.plusDays(w * 7L + day - 2));
        return dates;
    }
    private String weeks(int start, int total) {
        return "-".repeat(Math.min(start - 1, total)) + "1".repeat(Math.max(0, total - start + 1));
    }
    private String practiceWeeks(int firstWeek, int total) {
        var markers = new char[total];
        Arrays.fill(markers, '-');
        markers[firstWeek - 1] = '1';
        markers[firstWeek + 1] = '2';
        return new String(markers);
    }
    private void recordClassSlot(
            List<GeneratedClassSlot> outputList,
            List<TeachingCourseDTO> dbDtoList,
            TeacherCandidateDTO teacher,
            CourseDemandDTO course,
            String cohort,
            String groupName,
            String subGroup,
            String sessionType,
            int studentCount,
            Room room,
            int day,
            int startPeriod,
            int numPeriods,
            String weekSchedule,
            String phaseNote,
            int totalRoomsAvailable) {

        String typeTag = "PRACTICE".equals(sessionType) ? "[TH] " : "[LT] ";
        String displayCourseName = String.format("%s%s (Khóa %s - %s - %d SV)",
                typeTag, course.getCourseName(), cohort, groupName, studentCount);

        outputList.add(GeneratedClassSlot.builder()
                .baseCourseName(course.getCourseName())
                .credits(course.getCredits())
                .teacherId(teacher.getTeacherId())
                .teacherName(teacher.getTeacherName())
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .cohort(cohort)
                .classGroup(groupName)
                .subGroup(subGroup)
                .sessionType(sessionType)
                .studentCount(studentCount)
                .room(room.getRoomCode())
                .roomType(room.getRoomType())
                .roomCapacity(room.getCapacity())
                .dayOfWeek(day)
                .startPeriod(startPeriod)
                .numPeriods(numPeriods)
                .weekSchedule(weekSchedule)
                .phaseNote(phaseNote)
                .totalRoomsAvailable(totalRoomsAvailable)
                .totalClassesScheduled(outputList.size() + 1)
                .build());

        dbDtoList.add(TeachingCourseDTO.builder()
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .room(room.getRoomCode())
                .dayOfWeek(day)
                .startPeriod(startPeriod)
                .numPeriods(numPeriods)
                .weekSchedule(weekSchedule)
                .build());
    }


}
