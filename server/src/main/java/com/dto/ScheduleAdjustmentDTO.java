package com.dto;
import com.dto.CatalogDTO.*;
import java.time.*;
import java.util.List;

public final class ScheduleAdjustmentDTO {
    private ScheduleAdjustmentDTO() {}
    public record Edit(Long entryId, String classId, LocalDate date, Long roomId, Integer startPeriod, Integer numPeriods) {}
    public record Input(Long revision, Long baseVersionId, String snapshot, String action, List<Long> entryIds, List<String> classIds,
                        List<Edit> edits, Long lecturerId, Boolean locked, LocalDate fromDate, LocalDate toDate, List<Long> roomIds,
                        Long restoreVersionId, boolean overrideLocks, String reason) {}
    public record Commit(Input input, String previewToken) {}
    public record Options(long revision, Long baseVersionId, String snapshot, List<LecturerView> lecturers, List<AssignmentView> assignments,
                          List<RoomView> rooms, List<PeriodTimeDTO> periods, List<VisitingLecturerDTO.ClassChoice> classes) {}
    public record Session(Long id, String entryKey, String classId, String courseCode, String classGroup, String sessionType,
                          Long lecturerId, String lecturerCode, String lecturerName, String courseName, String parentClassId, int studentCount,
                          String roomCode, LocalDate date, LocalTime startTime, LocalTime endTime, int startPeriod, int numPeriods, boolean locked) {}
    public record Delta(String entryKey, Session before, Session after) {}
    public record Preview(String previewToken, long revision, Long baseVersionId, String action, String status, String message,
                          boolean canApply, boolean complete, boolean canConfirm, int unchangedEntries, int lockedEntries,
                          int beforeRequiredPeriods, int afterRequiredPeriods, int afterMissingPeriods, boolean configurationChanged,
                          List<Delta> changes, List<PlanBusyDTO> removedBusy, List<PlanBusyDTO> addedBusy, List<SchedulingPlanDTO.Conflict> conflicts) {}
    public record ChangeInfo(String action, Long sourceVersionId, Long restoredVersionId, List<String> entryKeys, String reason, String actor, Instant createdAt) {}
    public record Log(Long id, String action, Long sourceVersionId, Integer sourceVersionNumber, Long resultVersionId,
                      int resultVersionNumber, String actor, OffsetDateTime createdAt, int affectedEntries, String reason) {}
    public record LogPage(List<Log> rows, int page, int pageCount, long total) {}
}
