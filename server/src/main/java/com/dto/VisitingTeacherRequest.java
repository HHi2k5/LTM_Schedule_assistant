package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitingTeacherRequest {
    private String schoolId;          // Mã khoa (VD: PTIT)
    private String schoolName;        // Tên khoa
    private LocalDate semesterStartDate;
    private String teacherId;         // Mã GV thỉnh giảng (VD: GV_TG01)
    private String teacherName;       // Họ tên GV thỉnh giảng
    private String courseCode;        // Mã môn học
    private String courseName;        // Tên môn học
    private String cohort;            // Khóa (VD: 2024)
    private String classGroup;        // Lớp / Nhóm đã được giao (VD: Lớp 03 hoặc Lớp 01 - Tổ 2)
    private String sessionType;       // "THEORY" (LT) hoặc "PRACTICE" (TH)
    private Integer studentCount;     // Sĩ số lớp/nhóm
    private Integer requiredPeriods;  // Tổng số tiết cần xếp (VD: 30 hoặc 15)
    private Integer periodsPerSession;// Số tiết mỗi buổi (mặc định 3)
    private LocalDate studyStartDate; // Ngày bắt đầu học
    private LocalDate studyEndDate;   // Ngày kết thúc học
    private List<Integer> busyDaysOfWeek; // Các thứ bận trong tuần (VD: [2, 4] = Bận Thứ 2, Thứ 4)
    private String busyDatesText;     // Các ngày bận cụ thể cách nhau dấu phẩy (VD: "2026-08-17, 2026-08-24")
}