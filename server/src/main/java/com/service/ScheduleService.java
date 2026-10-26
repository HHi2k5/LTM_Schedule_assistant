package com.service;

import com.dto.*;
import com.model.*;
import com.repository.SchoolRepository;
import com.repository.TeacherRepository;
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

    @Transactional
    public void saveOrUpdateSchoolSchedule(SchoolUploadRequest request) {
        scheduleAccess.requireTeacherAccess(request.getTeacherId());
        // 1. Tìm hoặc tạo mới Giảng viên
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseGet(() -> Teacher.builder()
                        .teacherId(request.getTeacherId())
                        .fullName(request.getTeacherName() != null ? request.getTeacherName() : request.getTeacherId())
                        .semesterStartDate(request.getSemesterStartDate() != null ? request.getSemesterStartDate() : LocalDate.of(2026, 8, 10))
                        .build());

        if (request.getSemesterStartDate() != null) {
            teacher.setSemesterStartDate(request.getSemesterStartDate());
        }
        teacherRepository.save(teacher);

        // 2. Tìm xem trường này đã được up trước đó chưa
        String schoolCode = request.getSchool().getSchoolId();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacher.getTeacherId(), schoolCode)
                .orElseGet(() -> School.builder()
                        .schoolCode(schoolCode)
                        .teacher(teacher)
                        .build());

        school.setSchoolName(request.getSchool().getSchoolName());
        school.setColorCode(request.getSchool().getColorCode());

        // 3. Cập nhật khung giờ tiết (An toàn chống null tuyệt đối)
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

        // 4. Cập nhật danh sách môn học của trường này (An toàn chống null tuyệt đối)
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
                        .school(school)
                        .build());
            }
        }

        schoolRepository.save(school);
    }

    @Transactional
    public void deleteSchoolSchedule(String teacherId, String schoolCode) {
        scheduleAccess.requireTeacherAccess(teacherId);
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lịch của trường " + schoolCode + " cho giảng viên " + teacherId));
        
        Teacher teacher = school.getTeacher();
        teacher.getSchools().remove(school);
        schoolRepository.delete(school);
    }

    @Transactional(readOnly = true)
    public WeekScheduleResponse getWeeklySchedule(String teacherId, int weekIndex) {
        scheduleAccess.requireTeacherAccess(teacherId);
        Teacher teacher = teacherRepository.findTeacherWithFullSchedule(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dữ liệu giảng viên: " + teacherId));

        // Dự phòng an toàn chống NullPointerException nếu ngày khai giảng bị null
        LocalDate mondayOfWeek1 = teacher.getSemesterStartDate() != null 
                ? teacher.getSemesterStartDate() 
                : LocalDate.of(2026, 8, 10);

        LocalDate targetMonday = mondayOfWeek1.plusWeeks(weekIndex - 1);
        LocalDate targetSunday = targetMonday.plusDays(6);

        List<LessonSlot> weeklyLessons = new ArrayList<>();
        
        // Tập hợp khóa độc lập để lọc bỏ các bản ghi bị nhân bản (tránh trùng lặp trên giao diện)
        Set<String> uniqueLessonCheck = new HashSet<>();

        // Duyệt qua tất cả các trường đã lưu trong DB của giảng viên
        if (teacher.getSchools() != null) {
            for (School school : teacher.getSchools()) {
                if (school.getCourses() == null) continue;
                for (TeachingCourse course : school.getCourses()) {
                    String weekStr = course.getWeekSchedule();
                    if (weekStr == null || weekStr.isBlank()) continue;

                    int charIndex = weekIndex - 1;
                    if (charIndex >= 0 && charIndex < weekStr.length()) {
                        char flag = weekStr.charAt(charIndex);
                        if (flag != '-' && flag != ' ') {
                            int endPeriod = course.getStartPeriod() + course.getNumPeriods() - 1;
                            LocalTime startTime = getPeriodStartTime(school, course.getStartPeriod());
                            LocalTime endTime = getPeriodEndTime(school, endPeriod);

                            int offsetDays = (course.getDayOfWeek() == 8) ? 6 : (course.getDayOfWeek() - 2);
                            LocalDate classDate = targetMonday.plusDays(offsetDays);

                            // Khóa định danh duy nhất cho từng buổi học
                            String uniqueKey = course.getCourseName() + "_" + classDate.toString() + "_" + startTime.toString();
                            
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

        // Bắt trùng lịch theo giờ tuyệt đối
        detectConflicts(weeklyLessons);

        weeklyLessons.sort(Comparator.comparing(LessonSlot::getDate).thenComparing(LessonSlot::getStartTime));

        return WeekScheduleResponse.builder()
                .teacherId(teacherId)
                .weekIndex(weekIndex)
                .startDate(targetMonday)
                .endDate(targetSunday)
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
        // Fallback tự động tính giờ nếu thiếu tiết trong CSDL (mỗi tiết cách nhau 50 phút từ 7:00)
        return LocalTime.of(7, 0).plusMinutes((Math.max(1, period) - 1) * 50);
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
        // Fallback tự động tính giờ kết thúc (mỗi tiết học 50 phút)
        return LocalTime.of(7, 50).plusMinutes((Math.max(1, period) - 1) * 50);
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
