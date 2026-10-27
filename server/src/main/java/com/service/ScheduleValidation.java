package com.service;

import com.dto.*;
import java.time.*;
import java.util.*;

public final class ScheduleValidation {
    private ScheduleValidation() {}

    public static void text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " phải có từ 1 đến " + max + " ký tự.");
        }
    }

    public static void monday(LocalDate date) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY || date.getYear() < 2000 || date.getYear() > 2100) {
            throw new IllegalArgumentException("Ngày bắt đầu tuần 1 phải là Thứ Hai, trong năm 2000–2100.");
        }
    }

    public static Map<Integer, PeriodTimeDTO> periods(List<PeriodTimeDTO> values) {
        if (values == null || values.isEmpty() || values.size() > 30) {
            throw new IllegalArgumentException("Cần bảng giờ từ 1 đến 30 tiết.");
        }
        Map<Integer, PeriodTimeDTO> result = new TreeMap<>();
        for (var period : values) {
            if (period == null || period.getPeriod() < 1 || period.getPeriod() > 30
                    || period.getStart() == null || period.getEnd() == null || !period.getStart().isBefore(period.getEnd())
                    || result.putIfAbsent(period.getPeriod(), period) != null) {
                throw new IllegalArgumentException("Bảng giờ có tiết trùng, số tiết hoặc giờ không hợp lệ.");
            }
        }
        LocalTime previousEnd = null;
        for (var period : result.values()) {
            if (previousEnd != null && period.getStart().isBefore(previousEnd)) {
                throw new IllegalArgumentException("Giờ các tiết phải tăng dần và không chồng nhau.");
            }
            previousEnd = period.getEnd();
        }
        return result;
    }

    public static boolean hasPeriods(Map<Integer, PeriodTimeDTO> periods, int start, int count) {
        if (start < 1 || count < 1 || start > 30 || count > 30 || start + count - 1 > 30) return false;
        for (int p = start; p < start + count; p++) if (!periods.containsKey(p)) return false;
        return true;
    }

    public static void upload(SchoolUploadRequest request) {
        if (request == null || request.getSchool() == null) throw new IllegalArgumentException("Thiếu thông tin trường.");
        text(request.getTeacherId(), 50, "Mã GV");
        text(request.getSchool().getSchoolId(), 50, "Mã trường");
        text(request.getSchool().getSchoolName(), 255, "Tên trường");
        monday(request.getSemesterStartDate());
        var periods = periods(request.getSchool().getPeriods());
        if (request.getCourses() == null || request.getCourses().isEmpty() || request.getCourses().size() > 5000) {
            throw new IllegalArgumentException("Lịch phải có từ 1 đến 5000 dòng hợp lệ; dùng chức năng xóa để xóa lịch.");
        }
        int row = 0;
        for (var course : request.getCourses()) {
            row++;
            if (course == null) throw new IllegalArgumentException("Dòng " + row + ": thiếu môn học.");
            text(course.getCourseCode(), 50, "Dòng " + row + ": mã môn");
            text(course.getCourseName(), 255, "Dòng " + row + ": tên môn");
            text(course.getRoom(), 100, "Dòng " + row + ": phòng");
            if (course.getDayOfWeek() < 2 || course.getDayOfWeek() > 8
                    || !hasPeriods(periods, course.getStartPeriod(), course.getNumPeriods())) {
                throw new IllegalArgumentException("Dòng " + row + ": thứ hoặc dải tiết không có trong bảng giờ.");
            }
            if (course.getWeekSchedule() == null || !course.getWeekSchedule().matches("[a-zA-Z0-9 -]{1,100}")
                    || course.getWeekSchedule().replaceAll("[- ]", "").isEmpty()) {
                throw new IllegalArgumentException("Dòng " + row + ": chuỗi tuần không hợp lệ hoặc không có tuần học.");
            }
        }
    }
}
