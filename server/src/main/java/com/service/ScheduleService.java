package com.service;

import com.dto.*;
import com.model.*;
import com.repository.SchoolRepository;
import com.repository.TeacherRepository;
import com.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final com.security.ScheduleAccess scheduleAccess;
    private final ScheduleWriteLock writeLock;
    private final LecturerAccountService lecturerAccounts;

    @Transactional
    public void saveOrUpdateSchoolSchedule(SchoolUploadRequest request) {
        ScheduleValidation.upload(request);
        if (request.getCourses().stream().anyMatch(c -> c.getSchedulingPlanId() != null || c.getSchedulingEntryId() != null || "PLAN_CONFIRMED".equals(c.getSourceType())))
            throw new IllegalArgumentException("Không nhập lịch của bản lịch khoa qua luồng lịch cá nhân.");
        scheduleAccess.requireTeacherAccess(request.getTeacherId());
        writeLock.acquire();

        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseGet(() -> Teacher.builder()
                        .teacherId(request.getTeacherId())
                        .fullName(request.getTeacherName() != null ? request.getTeacherName() : request.getTeacherId())
                        .semesterStartDate(request.getSemesterStartDate() != null ? request.getSemesterStartDate() : LocalDate.of(2026, 8, 10))
                        .build());

        if (request.getSemesterStartDate() != null) {
            if (teacher.getSchools() != null && !teacher.getSchools().isEmpty()
                    && !request.getSemesterStartDate().equals(teacher.getSemesterStartDate())) {
                throw new IllegalArgumentException("Mốc tuần khác lịch đã lưu; cần đối chiếu trước khi thay đổi.");
            }
            teacher.setSemesterStartDate(request.getSemesterStartDate());
        }
        if (teacher.getUsername() == null || teacher.getUsername().isBlank())
            lecturerAccounts.ensure(request.getTeacherId(), teacher.getFullName(), teacher, teacher.getSemesterStartDate());
        teacherRepository.save(teacher);

        String schoolCode = request.getSchool().getSchoolId();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacher.getTeacherId(), schoolCode)
                .orElseGet(() -> School.builder()
                        .schoolCode(schoolCode)
                        .teacher(teacher)
                        .build());
        if (school.getCourses().stream().anyMatch(c -> c.getSchedulingPlanId() != null))
            throw new IllegalArgumentException("Trường có lịch thuộc bản lịch khoa đã xác nhận. Hãy chỉnh bản nháp của khoa để giữ đúng phạm vi.");

        school.setSchoolName(request.getSchool().getSchoolName());
        school.setColorCode(request.getSchool().getColorCode());

        if (school.getPeriods() == null) {
            school.setPeriods(new HashSet<>());
        } else {
            school.getPeriods().clear();
        }

        if (request.getSchool().getPeriods() != null) {
            for (PeriodTimeDTO pt : request.getSchool().getPeriods()) {
                school.getPeriods().add(SchoolPeriod.builder()
                        .periodNumber(pt.getPeriod())
                        .startTime(pt.getStart())
                        .endTime(pt.getEnd())
                        .school(school)
                        .build());
            }
        }

        if (school.getCourses() == null) {
            school.setCourses(new HashSet<>());
        } else {
            school.getCourses().clear();
        }

        if (request.getCourses() != null) {
            for (TeachingCourseDTO dto : request.getCourses()) {
                school.getCourses().add(TeachingCourse.builder()
                        .courseCode(dto.getCourseCode())
                        .sourceType(dto.getSourceType() == null ? "EXCEL_UPLOAD" : dto.getSourceType())
                        .scheduleClassId(dto.getScheduleClassId())
                        .scheduleParentId(dto.getScheduleParentId())
                        .sessionType(dto.getSessionType())
                        .planSnapshot(dto.getPlanSnapshot())
                        .courseName(dto.getCourseName())
                        .classroom(dto.getRoom())
                        .dayOfWeek(dto.getDayOfWeek())
                        .startPeriod(dto.getStartPeriod())
                        .numPeriods(dto.getNumPeriods())
                        .weekSchedule(dto.getWeekSchedule())
                        .customStartTime(dto.getCustomStartTime())
                        .customEndTime(dto.getCustomEndTime())
                        .school(school)
                        .build());
            }
        }

        if (teacher.getSchools().stream().noneMatch(existing -> existing == school || existing.getSchoolCode().equalsIgnoreCase(schoolCode))) {
            teacher.getSchools().add(school);
        }
        schoolRepository.save(school);
    }

    /** Replace only rows managed by the weekly school-wide planner, including withdrawn teachers. */
    @Transactional(rollbackFor = Exception.class)
    public void replaceWeeklyGenerated(SchoolWideScheduleRequest request, Map<String, List<TeachingCourseDTO>> generated,
                                       Map<String, String> names) {
        writeLock.acquire();
        String schoolCode = request.getTargetSchool().getSchoolId();
        var existing = teacherRepository.findAllWithFullSchedule();
        var retained = new LinkedHashMap<String, List<TeachingCourseDTO>>();
        for (var teacher : existing) for (var school : teacher.getSchools()) {
            if (!schoolCode.equals(school.getSchoolCode())) continue;
            boolean managed = school.getCourses().stream().anyMatch(c -> "WEEKLY_AUTO".equals(c.getSourceType()));
            if (!managed && !generated.containsKey(teacher.getTeacherId())) continue;
            var rows = new ArrayList<TeachingCourseDTO>();
            for (var c : school.getCourses()) if (!"WEEKLY_AUTO".equals(c.getSourceType())) {
                rows.add(TeachingCourseDTO.builder().courseCode(c.getCourseCode()).courseName(c.getCourseName())
                        .room(c.getClassroom()).dayOfWeek(c.getDayOfWeek()).startPeriod(c.getStartPeriod())
                        .numPeriods(c.getNumPeriods()).weekSchedule(c.getWeekSchedule()).customStartTime(c.getCustomStartTime())
                        .customEndTime(c.getCustomEndTime()).sourceType(c.getSourceType()).scheduleClassId(c.getScheduleClassId())
                        .scheduleParentId(c.getScheduleParentId()).sessionType(c.getSessionType()).planSnapshot(c.getPlanSnapshot()).build());
            }
            if (!rows.isEmpty() && !request.getTargetSchool().getPeriods().equals(school.getPeriods().stream()
                    .sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber))
                    .map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList())) {
                throw new IllegalArgumentException("Bảng tiết của lịch giữ lại khác cấu hình xếp lịch: " + teacher.getTeacherId());
            }
            retained.put(teacher.getTeacherId(), rows);
            names.putIfAbsent(teacher.getTeacherId(), teacher.getFullName());
        }
        generated.forEach((id, rows) -> retained.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(rows));
        for (var entry : retained.entrySet()) {
            if (entry.getValue().isEmpty()) deleteSchoolSchedule(entry.getKey(), schoolCode);
            else saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(entry.getKey()).teacherName(names.get(entry.getKey()))
                    .semesterStartDate(request.getSemesterStartDate()).school(request.getTargetSchool()).courses(entry.getValue()).build());
        }
    }

    @Transactional
    public void deleteSchoolSchedule(String teacherId, String schoolCode) {
        scheduleAccess.requireTeacherAccess(teacherId);
        writeLock.acquire();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch của trường " + schoolCode + " cho giảng viên " + teacherId));
        if (school.getCourses().stream().anyMatch(c -> c.getSchedulingPlanId() != null))
            throw new IllegalArgumentException("Không xóa lịch của bản lịch khoa qua luồng lịch cá nhân.");

        Teacher teacher = school.getTeacher();
        teacher.getSchools().remove(school);
        schoolRepository.delete(school);
    }

    /** Replace only this plan's projection; other course rows retain their identities and clocks. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    void replacePlanProjection(Long planId, SchoolWideScheduleRequest request, List<SchedulingEntry> entries,
                               Map<Long, String> identities) {
        writeLock.acquire();
        String code = request.getTargetSchool().getSchoolId();
        var intended = new LinkedHashMap<String, List<SchedulingEntry>>();
        entries.forEach(e -> intended.computeIfAbsent(identities.get(e.getLecturer().getId()), ignored -> new ArrayList<>()).add(e));
        for (var teacher : teacherRepository.findAllWithFullSchedule()) {
            var school = teacher.getSchools().stream().filter(s -> code.equalsIgnoreCase(s.getSchoolCode())).findFirst().orElse(null);
            var added = intended.getOrDefault(teacher.getTeacherId(), List.of());
            boolean owns = school != null && school.getCourses().stream().anyMatch(c -> planId.equals(c.getSchedulingPlanId()));
            if (!owns && added.isEmpty()) continue;
            scheduleAccess.requireTeacherAccess(teacher.getTeacherId());
            boolean retained = teacher.getSchools().stream().flatMap(s -> s.getCourses().stream()).anyMatch(c -> !planId.equals(c.getSchedulingPlanId()));
            if (!added.isEmpty() && retained && !request.getSemesterStartDate().equals(teacher.getSemesterStartDate()))
                throw new IllegalArgumentException("Mốc tuần của GV khác lịch được giữ lại; cần đối chiếu trước.");
            if (school == null) {
                school = School.builder().schoolCode(code).schoolName(request.getTargetSchool().getSchoolName()).colorCode(request.getTargetSchool().getColorCode()).teacher(teacher).build();
                teacher.getSchools().add(school);
            }
            school.getCourses().removeIf(c -> planId.equals(c.getSchedulingPlanId()));
            if (!added.isEmpty()) {
                if (!school.getCourses().isEmpty()) {
                    var previous = school.getPeriods().stream().sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber)).map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList();
                    if (!previous.equals(request.getTargetSchool().getPeriods())) throw new IllegalArgumentException("Bảng giờ khác lịch của khoa hoặc lịch cá nhân được giữ lại.");
                } else {
                    school.getPeriods().clear();
                    for (var p : request.getTargetSchool().getPeriods()) school.getPeriods().add(SchoolPeriod.builder().school(school).periodNumber(p.getPeriod()).startTime(p.getStart()).endTime(p.getEnd()).build());
                }
                if (!retained) teacher.setSemesterStartDate(request.getSemesterStartDate());
                for (var e : added) {
                    int week = (int) java.time.temporal.ChronoUnit.DAYS.between(request.getSemesterStartDate(), e.getLessonDate()) / 7;
                    school.getCourses().add(TeachingCourse.builder().school(school).sourceType("PLAN_CONFIRMED").schedulingPlanId(planId).schedulingEntryId(e.getId())
                            .courseCode(e.getDemand().getCourseCode()).courseName(e.getCourseName()).classroom(e.getRoomCode()).dayOfWeek(e.getLessonDate().getDayOfWeek().getValue() + 1)
                            .startPeriod(e.getStartPeriod()).numPeriods(e.getNumPeriods()).weekSchedule("-".repeat(week) + "1")
                            .scheduleClassId(e.getScheduleClassId()).scheduleParentId(e.getScheduleParentId()).sessionType(e.getSessionType()).planSnapshot(e.getDemand().getSnapshotJson()).build());
                }
            }
            schoolRepository.saveAndFlush(school);
        }
    }

    @Transactional(readOnly = true)
    public WeekScheduleResponse getWeeklySchedule(String teacherId, int weekIndex) {
        scheduleAccess.requireTeacherAccess(teacherId);
        if (weekIndex < 1 || weekIndex > 100) throw new IllegalArgumentException("Tuần phải từ 1 đến 100.");
        Teacher teacher = teacherRepository.findTeacherWithFullSchedule(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu giảng viên: " + teacherId));

        LocalDate mondayOfWeek1 = teacher.getSemesterStartDate();
        ScheduleValidation.monday(mondayOfWeek1);

        LocalDate targetMonday = mondayOfWeek1.plusWeeks(weekIndex - 1);
        LocalDate targetSunday = targetMonday.plusDays(6);

        List<LessonSlot> weeklyLessons = new ArrayList<>();
        Map<String, SchoolConfigDTO> schoolConfigs = new TreeMap<>();
        Set<Object> uniqueLessonCheck = new HashSet<>();

        if (teacher.getSchools() != null) {
            for (School school : teacher.getSchools()) {
                schoolConfigs.computeIfAbsent(school.getSchoolCode(), code -> SchoolConfigDTO.builder()
                        .schoolId(code)
                        .schoolName(school.getSchoolName())
                        .colorCode(school.getColorCode() != null ? school.getColorCode() : "#177c62")
                        .periods(school.getPeriods() == null ? List.of() : school.getPeriods().stream()
                                .sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber))
                                .map(period -> new PeriodTimeDTO(period.getPeriodNumber(), period.getStartTime(), period.getEndTime()))
                                .toList())
                        .build());
                if (school.getCourses() == null) continue;
                for (TeachingCourse course : school.getCourses()) {
                    String weekStr = course.getWeekSchedule();
                    if (weekStr == null || weekStr.isBlank()) continue;

                    int charIndex = weekIndex - 1;
                    if (charIndex >= 0 && charIndex < weekStr.length()) {
                        char flag = weekStr.charAt(charIndex);
                        if (flag != '-' && flag != ' ') {
                            int endPeriod = course.getStartPeriod() + course.getNumPeriods() - 1;

                            // ƯU TIÊN LẤY GIỜ BẮT ĐẦU & KẾT THÚC TỪ FILE EXCEL ĐẦU VÀO (NẾU CÓ)
                            LocalTime startTime = (course.getCustomStartTime() != null)
                                    ? course.getCustomStartTime()
                                    : getPeriodStartTime(school, course.getStartPeriod());
                            LocalTime endTime = (course.getCustomEndTime() != null)
                                    ? course.getCustomEndTime()
                                    : getPeriodEndTime(school, endPeriod);

                            int offsetDays = (course.getDayOfWeek() == 8) ? 6 : (course.getDayOfWeek() - 2);
                            LocalDate classDate = targetMonday.plusDays(offsetDays);

                            Object uniqueKey = course.getId() != null ? course.getId() : course;

                            if (!uniqueLessonCheck.contains(uniqueKey)) {
                                uniqueLessonCheck.add(uniqueKey);

                                weeklyLessons.add(LessonSlot.builder()
                                        .schoolId(school.getSchoolCode())
                                        .schoolName(school.getSchoolName())
                                        .colorCode(school.getColorCode() != null ? school.getColorCode() : "#177c62")
                                        .courseName(course.getCourseName())
                                        .room(course.getClassroom())
                                        .dayOfWeek(course.getDayOfWeek())
                                        .date(classDate)
                                        .startPeriod(course.getStartPeriod())
                                        .endPeriod(endPeriod)
                                        .startTime(startTime)
                                        .endTime(endTime)
                                        .isConflict(false)
                                        .build());
                            }
                        }
                    }
                }
            }
        }

        detectConflicts(weeklyLessons);
        weeklyLessons.sort(Comparator.comparing(LessonSlot::getDate).thenComparing(LessonSlot::getStartTime));

        return WeekScheduleResponse.builder()
                .teacherId(teacherId)
                .weekIndex(weekIndex)
                .startDate(targetMonday)
                .endDate(targetSunday)
                .schools(new ArrayList<>(schoolConfigs.values()))
                .lessons(weeklyLessons)
                .build();
    }

    private LocalTime getPeriodStartTime(School school, int period) {
        if (school != null && school.getPeriods() != null) {
            Optional<LocalTime> time = school.getPeriods().stream()
                    .filter(p -> p != null && p.getPeriodNumber() == period)
                    .map(SchoolPeriod::getStartTime)
                    .findFirst();
            if (time.isPresent() && time.get() != null) {
                return time.get();
            }
        }
        throw new IllegalArgumentException("Thiếu giờ bắt đầu tiết " + period + ". Cần sửa bảng giờ của trường.");
    }

    private LocalTime getPeriodEndTime(School school, int period) {
        if (school != null && school.getPeriods() != null) {
            Optional<LocalTime> time = school.getPeriods().stream()
                    .filter(p -> p != null && p.getPeriodNumber() == period)
                    .map(SchoolPeriod::getEndTime)
                    .findFirst();
            if (time.isPresent() && time.get() != null) {
                return time.get();
            }
        }
        throw new IllegalArgumentException("Thiếu giờ kết thúc tiết " + period + ". Cần sửa bảng giờ của trường.");
    }

    private void detectConflicts(List<LessonSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                LessonSlot a = slots.get(i);
                LessonSlot b = slots.get(j);

                if (a.getDate().isEqual(b.getDate())) {
                    LocalTime maxStart = a.getStartTime().isAfter(b.getStartTime()) ? a.getStartTime() : b.getStartTime();
                    LocalTime minEnd = a.getEndTime().isBefore(b.getEndTime()) ? a.getEndTime() : b.getEndTime();

                    if (maxStart.isBefore(minEnd)) {
                        a.setConflict(true);
                        b.setConflict(true);
                        a.setConflictReason("Trùng ca dạy với " + b.getCourseName() + " (" + b.getSchoolName() + ")");
                        b.setConflictReason("Trùng ca dạy với " + a.getCourseName() + " (" + a.getSchoolName() + ")");
                    }
                }
            }
        }
    }
}
