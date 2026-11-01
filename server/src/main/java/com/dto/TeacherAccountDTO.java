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
public class TeacherAccountDTO {
    private Long subjectAssignmentId;
    private String schoolCode;
    private String teacherId;         // Mã GV (đồng thời là Username)
    private String teacherName;       // Họ tên GV
    private String username;          // Tự động = Mã GV
    private String defaultPassword;   // Tự động = tenkhongdauvietlien + Mã GV
    private String courseCode;        // Dùng khi gửi lẻ 1 môn
    private String courseName;
    private String cohort;
    private Integer theoryWeight;
    private Integer practiceWeight;

    // Danh sách các môn học mà giảng viên này phụ trách (1 GV dạy nhiều môn)
    private List<SubjectAssignment> subjects;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubjectAssignment {
        private Long id;
        private String courseCode;
        private String courseName;
        private String cohort;
        private Integer theoryWeight;
        private Integer practiceWeight;
    }
}