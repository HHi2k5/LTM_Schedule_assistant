package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherCandidateDTO {
    private Integer theoryWeight;
    private Integer practiceWeight;
    private String teacherId;             
    private String teacherName;            
    private Integer assignedClasses;       
}
