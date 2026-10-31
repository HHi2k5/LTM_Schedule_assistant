package com.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClassWorkloadDTO {
    private Integer demandIndex;
    private String classId;
    private String parentClassId;
    private Integer studentCount;
    private String courseCode;
    private String cohort;
    private String classGroup;
    private String sessionType;
    private String teacherId;
    private int requiredPeriods;
    private int scheduledPeriods;
    private int missingPeriods;
}
