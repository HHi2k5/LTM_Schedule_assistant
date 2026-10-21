package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDemandDTO {
    private String courseCode;             // VD: "INT14189"
    private String courseName;             // VD: "Lập trình mạng"
    private int totalClassesToOpen;        // Tổng số lớp cần mở của môn này (VD: 10 lớp)
    private List<TeacherCandidateDTO> teachers; // Các giảng viên cùng phụ trách dạy môn này
}