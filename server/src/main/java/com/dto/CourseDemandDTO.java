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
    private String stt;                    // STT dòng trong kế hoạch mở lớp
    private String courseCode;             // VD: "61FIT3DBS"
    private String courseName;             // VD: "Cơ sở dữ liệu"
    private int totalClassesToOpen;        // Số lớp mở ở dòng này (VD: 1)
    private int classSize;                 // Sĩ số lớp (VD: 50, 60, 100, 150)
    private String cohort;                 // Khóa (VD: "2024")
    private int startWeek;                 // Tuần bắt đầu (VD: 1)
    private Integer fixedDayOfWeek;        // Thứ cố định lấy từ file Excel (2 = Thứ 2 ... 7 = Thứ 7, null nếu tự động)
    private Integer fixedStartPeriod;      // Tiết bắt đầu lấy từ file Excel (VD: 1, 4, 7, 10, null nếu tự động)
    private int numPeriodsPerSession;      // Số tiết (VD: 3)
    private Boolean hasPractice;           // Có chia lịch thực hành theo tổ hay không (mặc định true)
    private List<TeacherCandidateDTO> teachers; // Danh sách giảng viên phụ trách môn & khóa này
}