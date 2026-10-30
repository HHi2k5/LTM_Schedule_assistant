package com.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TeacherAllocationDTO {
    private String courseCode;
    private String cohort;
    private String teacherId;
    private String teacherName;
    private String sessionType;
    private int weight;
    private int targetClasses;
    private int assignedClasses;
    private int scheduledPeriods;
}
