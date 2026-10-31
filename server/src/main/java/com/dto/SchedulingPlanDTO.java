package com.dto;

import java.time.*;
import java.util.List;

public final class SchedulingPlanDTO {
    private SchedulingPlanDTO() {}
    public record Create(String schoolCode, Long departmentId, String name, String semester, LocalDate semesterStartDate) {}
    public record Revision(Long revision) {}
    public record Summary(Long id, String schoolCode, String schoolName, Long departmentId, String departmentName,
                          String name, String semester, LocalDate semesterStartDate, long revision, String state,
                          Integer draftVersion, Integer confirmedVersion, LocalDateTime updatedAt) {}
    public record VersionSummary(Long id, int number, boolean complete, LocalDateTime createdAt, String createdBy,
                                  LocalDateTime confirmedAt, boolean currentDraft, boolean currentConfirmed) {}
    public record Version(VersionSummary summary, CatalogSchedulingDTO.Configuration configuration,
                          List<CourseDemandDTO> courses, AutoScheduleResponse result, long entryCount,
                          VisitingLecturerDTO.ChangeInfo visitingChange, List<PlanBusyDTO> busySlots,
                          ScheduleAdjustmentDTO.ChangeInfo adjustmentChange, boolean customized) {}
    public record Detail(Summary plan, Version draft, List<VersionSummary> versions) {}
    public record Entry(Long id, Long lecturerId, String lecturerCode, String lecturerName, String courseCode,
                         String courseName, String cohort, String classGroup, String sessionType, int studentCount,
                         String roomCode, LocalDate date, LocalTime startTime, LocalTime endTime, int startPeriod, int numPeriods, String entryKey, boolean locked) {}
    public record EntryPage(List<Entry> rows, int page, int pageCount, long total) {}
    public record Conflict(String type, String severity, List<Long> entryIds, LocalDate date, LocalTime startTime,
                            LocalTime endTime, String resource, String message) {}
}
