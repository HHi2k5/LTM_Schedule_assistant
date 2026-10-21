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
public class SchoolWideScheduleRequest {
    private LocalDate semesterStartDate;   // Ngày Thứ 2 của Tuần 1 (VD: "2026-08-10")
    private int totalWeeks;                // Tổng số tuần học (Mặc định: 16)
    private String buildingSuffix;         // Ký hiệu tòa nhà (VD: "A2" -> Phòng "101-A2"..."705-A2")
    private SchoolConfigDTO targetSchool;  // Thông tin trường & khung giờ 10 tiết (Sáng 1-5, Chiều 6-10)
    private List<CourseDemandDTO> courses; // Danh sách các môn học toàn trường cần mở lớp
}