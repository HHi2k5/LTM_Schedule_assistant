package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeachingCourseDTO {
    private String courseCode;
    private String courseName;
    private String room;
    private int dayOfWeek; 
    private int startPeriod;
    private int numPeriods;
    private String weekSchedule;
}