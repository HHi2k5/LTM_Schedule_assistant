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

    @Transactional
    public void saveOrUpdateSchoolSchedule(SchoolUploadRequest request) {
        ScheduleValidation.upload(request);
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
        teacherRepository.save(teacher);

        String schoolCode = request.getSchool().getSchoolId();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacher.getTeacherId(), schoolCode)
                .orElseGet(() -> School.builder()
                        .schoolCode(schoolCode)
                        .teacher(teacher)
                        .build());

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

        schoolRepository.save(school);
    }

    @Transactional
    public void deleteSchoolSchedule(String teacherId, String schoolCode) {
        scheduleAccess.requireTeacherAccess(teacherId);
        writeLock.acquire();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch của trường " + schoolCode + " cho giảng viên " + teacherId));

        Teacher teacher = school.getTeacher();
        teacher.getSchools().remove(school);
        schoolRepository.delete(school);
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