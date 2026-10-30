package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeachingCourseDTO {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String sourceType;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String scheduleClassId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String scheduleParentId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String sessionType;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String planSnapshot;
    private String courseCode;
    private String courseName;
    private String room;
    private int dayOfWeek;
    private int startPeriod;
    private int numPeriods;
    private String weekSchedule;
    private LocalTime customStartTime; // Giờ bắt đầu lấy trực tiếp từ cột J file Excel (VD: 07:15 hoặc 12:00)
    private LocalTime customEndTime;   // Giờ kết thúc lấy trực tiếp từ cột K file Excel (VD: 09:45 hoặc 13:00)
}
