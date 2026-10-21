package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedClassSlot {
    private String teacherId;
    private String teacherName;
    private String courseCode;
    private String courseName;
    private String classGroup;    
    private String room;
    private int dayOfWeek;
    private int startPeriod;
    private int numPeriods;
    private String weekSchedule;  
    private String phaseNote; 
    private int totalRoomsAvailable; // 35 phòng
    private int totalClassesScheduled;    
}