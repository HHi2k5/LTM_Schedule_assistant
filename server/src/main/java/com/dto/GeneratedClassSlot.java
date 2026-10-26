package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedClassSlot {
    private String baseCourseName;
    private Integer credits;
    private String teacherId;
    private String teacherName;
    private String courseCode;
    private String courseName;
    private String cohort;              // Khóa (VD: "2024")
    private String classGroup;          // Nhóm lớp (VD: "Lớp 01" hoặc "Lớp 01 - Tổ 1")
    private String subGroup;            // "Cả lớp" (Lý thuyết) hoặc "Tổ 1", "Tổ 2" (Thực hành)
    private String sessionType;         // "THEORY" hoặc "PRACTICE"
    private int studentCount;           // Sĩ số của ca học
    private String room;                // Mã phòng được xếp
    private String roomType;            // Loại phòng (THEORY_LARGE, THEORY_STANDARD, LAB_PRACTICE...)
    private int roomCapacity;           // Sức chứa phòng
    private int dayOfWeek;              // Thứ (2 -> 7)
    private int startPeriod;            // Tiết bắt đầu
    private int numPeriods;             // Số tiết
    private String weekSchedule;        // Chuỗi tuần học
    private String phaseNote;           // Ghi chú chi tiết
    private int totalRoomsAvailable;
    private int totalClassesScheduled;
}
