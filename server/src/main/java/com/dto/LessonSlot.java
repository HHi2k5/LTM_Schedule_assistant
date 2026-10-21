package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonSlot {
    private String schoolId;
    private String schoolName;
    private String colorCode;
    private String courseName;
    private String room;
    private int dayOfWeek;
    private LocalDate date;
    private int startPeriod;
    private int endPeriod;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean isConflict;
    private String conflictReason;
}