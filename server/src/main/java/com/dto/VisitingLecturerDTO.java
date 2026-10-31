package com.dto;
import com.dto.CatalogDTO.*;
import java.time.*;
import java.util.List;

public final class VisitingLecturerDTO {
    private VisitingLecturerDTO() {}
    public record Availability(LocalDate startDate, LocalDate endDate, Integer dayOfWeek, LocalTime startTime, LocalTime endTime, String reason) {}
    public record FixedSession(String classId, LocalDate date, Long roomId, Integer startPeriod, Integer numPeriods) {}
    public record NewClass(String courseCode, String courseName, String cohort, String classGroup, String sessionType,
                           Integer studentCount, Integer credits, LocalDate studyStartDate, LocalDate studyEndDate, Integer totalPeriods, String parentClassId) {}
    public record Input(Long revision, Long baseVersionId, String snapshot, Long lecturerId, String action, String timing,
                        List<String> classIds, NewClass newClass, List<FixedSession> sessions, List<Availability> busySlots) {}
    public record Commit(Input input, String previewToken) {}
    public record Profile(Long revision, String lecturerCode, String fullName, String accountTeacherId, List<AssignmentInput> assignments) {}
    public record ClassChoice(String classId, String parentClassId, int demandIndex, String courseCode, String courseName, String cohort,
                              String classGroup, String sessionType, int studentCount, int credits, LocalDate studyStartDate, LocalDate studyEndDate,
                              int requiredPeriods, int scheduledPeriods, List<String> lecturerCodes) {}
    public record Options(Long baseVersionId, long revision, String snapshot, List<LecturerView> lecturers, List<AssignmentView> assignments,
                           List<RoomView> rooms, List<PeriodTimeDTO> periods, List<ClassChoice> classes, List<PlanBusyDTO> busySlots) {}
    public record ClassChange(String classId, String courseCode, String classGroup, String sessionType, List<String> beforeLecturers,
                              String afterLecturer, int beforePeriods, int afterPeriods, int requiredPeriods) {}
    public record Session(String classId, String courseCode, String classGroup, String lecturerCode, String roomCode,
                           LocalDate date, LocalTime startTime, LocalTime endTime, int startPeriod, int numPeriods) {}
    public record Preview(String previewToken, long revision, Long baseVersionId, String lecturerCode, String action, String timing,
                          boolean complete, boolean canConfirm, int unchangedEntries, List<ClassChange> changes,
                          List<Session> beforeEntries, List<Session> afterEntries, List<PlanBusyDTO> busySlots, List<SchedulingPlanDTO.Conflict> conflicts) {}
    public record ChangeInfo(Long sourceVersionId, Long lecturerId, String lecturerCode, String action, String timing, List<String> classIds, String createdBy, Instant createdAt) {}
}
