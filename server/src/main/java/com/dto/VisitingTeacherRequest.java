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

    // Hỗ trợ nhận danh sách nhiều môn học / lớp học phân công cho thỉnh giảng
    private List<VisitingAssignmentDTO> assignments;

    private LocalDate studyStartDate; // Ngày bắt đầu học chung
    private LocalDate studyEndDate;   // Ngày kết thúc học chung
    private List<Integer> busyDaysOfWeek; // Các thứ bận trong tuần (VD: [2, 4])
    private String busyDatesText;     // Các ngày bận cụ thể cách nhau dấu phẩy
}