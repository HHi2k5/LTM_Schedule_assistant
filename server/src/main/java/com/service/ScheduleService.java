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

    @Transactional
    public void saveOrUpdateSchoolSchedule(SchoolUploadRequest request) {
        // 1. Tìm hoặc tạo mới Giảng viên
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseGet(() -> Teacher.builder()
                        .teacherId(request.getTeacherId())
                        .fullName(request.getTeacherName() != null ? request.getTeacherName() : request.getTeacherId())
                        .semesterStartDate(request.getSemesterStartDate())
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

        // 3. Cập nhật khung giờ tiết (xóa cũ tạo mới nếu up lại)
        school.getPeriods().clear();
        for (PeriodTimeDTO pt : request.getSchool().getPeriods()) {
            school.getPeriods().add(SchoolPeriod.builder()
                    .periodNumber(pt.getPeriod())
                    .startTime(pt.getStart())
                    .endTime(pt.getEnd())
                    .school(school)
                    .build());
        }

        // 4. Cập nhật danh sách môn học của trường này
        school.getCourses().clear();
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
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lịch của trường " + schoolCode + " cho giảng viên " + teacherId));
        
        // Xóa khỏi danh sách của Teacher (orphanRemoval = true sẽ tự xóa sạch trong DB)
        Teacher teacher = school.getTeacher();
        teacher.getSchools().remove(school);
        schoolRepository.delete(school);
}
    @Transactional(readOnly = true)
    public WeekScheduleResponse getWeeklySchedule(String teacherId, int weekIndex) {
        Teacher teacher = teacherRepository.findTeacherWithFullSchedule(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dữ liệu giảng viên: " + teacherId));

        LocalDate mondayOfWeek1 = teacher.getSemesterStartDate();
        LocalDate targetMonday = mondayOfWeek1.plusWeeks(weekIndex - 1);
        LocalDate targetSunday = targetMonday.plusDays(6);

        List<LessonSlot> weeklyLessons = new ArrayList<>();

        // Duyệt qua tất cả các trường đã lưu trong DB của giảng viên
        for (School school : teacher.getSchools()) {
            for (TeachingCourse course : school.getCourses()) {
                String weekStr = course.getWeekSchedule();
                int charIndex = weekIndex - 1;

                if (charIndex < weekStr.length() && weekStr.charAt(charIndex) != '-' && weekStr.charAt(charIndex) != ' ') {
                    int endPeriod = course.getStartPeriod() + course.getNumPeriods() - 1;
                    LocalTime startTime = getPeriodStartTime(school, course.getStartPeriod());
                    LocalTime endTime = getPeriodEndTime(school, endPeriod);

                    int offsetDays = (course.getDayOfWeek() == 8) ? 6 : (course.getDayOfWeek() - 2);
                    LocalDate classDate = targetMonday.plusDays(offsetDays);

                    weeklyLessons.add(LessonSlot.builder()
                            .schoolId(school.getSchoolCode())
                            .schoolName(school.getSchoolName())
                            .colorCode(school.getColorCode())
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
        return school.getPeriods().stream()
                .filter(p -> p.getPeriodNumber() == period)
                .map(SchoolPeriod::getStartTime)
                .findFirst()
                .orElse(LocalTime.of(7, 0));
    }

    private LocalTime getPeriodEndTime(School school, int period) {
        return school.getPeriods().stream()
                .filter(p -> p.getPeriodNumber() == period)
                .map(SchoolPeriod::getEndTime)
                .findFirst()
                .orElse(LocalTime.of(11, 0));
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