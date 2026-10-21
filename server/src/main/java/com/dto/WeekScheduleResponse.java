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
public class WeekScheduleResponse {
    private String teacherId;
    private int weekIndex;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<LessonSlot> lessons;
}