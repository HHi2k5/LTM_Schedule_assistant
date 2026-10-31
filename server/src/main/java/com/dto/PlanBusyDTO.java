package com.dto;
import java.time.*;
public record PlanBusyDTO(Long lecturerId, LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {}
