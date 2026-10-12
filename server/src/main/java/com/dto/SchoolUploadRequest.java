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
public class SchoolUploadRequest {
    private String teacherId;
    private String teacherName;
    private LocalDate semesterStartDate;
    private SchoolConfigDTO school;
    private List<TeachingCourseDTO> courses;
}