package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitingAssignmentDTO {
    private String courseCode;        // Mã môn học (VD: 61FIT3DBS)
    private String courseName;        // Tên môn học
    private String cohort;            // Khóa (VD: 2024)
    private String classGroup;        // Lớp / Nhóm (VD: Lớp 01 hoặc Tổ 2)
    private String sessionType;       // "THEORY" (LT) hoặc "PRACTICE" (TH)
    private Integer studentCount;     // Sĩ số SV
    private Integer requiredPeriods;  // Tổng số tiết cần xếp
}