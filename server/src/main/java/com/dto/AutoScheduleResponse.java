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
public class AutoScheduleResponse {
    private String status;
    private String message;
    private int totalRoomsAvailable;
    private int totalClassesScheduled;
    private List<GeneratedClassSlot> scheduledClasses;
    private List<String> unscheduledWarnings; // Cảnh báo nếu hết phòng hoặc kín lịch
}