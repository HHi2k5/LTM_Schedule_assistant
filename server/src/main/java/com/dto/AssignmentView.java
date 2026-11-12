package com.dto;

import java.time.LocalDate;
import java.util.List;


public final class AssignmentView {
    private AssignmentView() {}

    public record Session(int dayOfWeek, int startPeriod, int numPeriods, String room, String weekSchedule) {}

    
    public record Candidate(String teacherId, String teacherName, String teacherType, String kind, int weight,
                            String blockedReason) {}

    public record ClassRow(String classId, String courseCode, String courseName, String label, String sessionType,
                           Long departmentId, String departmentName, String teacherId, String teacherName,
                           int totalPeriods, String workflowStatus, List<Session> sessions, List<Candidate> candidates) {}

    public record TeacherLoad(String teacherId, String teacherName, String teacherType, int classes, int periods) {}


    public record DepartmentStatus(Long departmentId, String departmentCode, String departmentName,
                                   String headTeacherId, String headTeacherName,
                                   int totalClasses, int assignedClasses, String workflowStatus) {}

    public record Overview(String schoolId, Long departmentId, LocalDate semesterStartDate,
                           String workflowStatus, List<DepartmentStatus> departmentStatuses,
                           List<ClassRow> classes, List<TeacherLoad> teachers) {}

   
    public record AssignRequest(String schoolId, String classId, String teacherId) {}

    public record AutoRequest(String schoolId, Long departmentId) {}

    public record WorkflowRequest(String schoolId, Long departmentId, String action) {}

    public record Unassigned(String classId, String label, String reason) {}

    public record AutoResult(String status, String message, int assigned, List<Unassigned> remaining) {}


    public record BusyEntry(Long id, Integer dayOfWeek, LocalDate specificDate, String note) {}

    public record ExternalSlot(String schoolId, String schoolName, String courseName, int dayOfWeek,
                               int startPeriod, int numPeriods, String room, String weekSchedule) {}

    public record VisitingProfile(String teacherId, String teacherName, Long departmentId, String departmentName,
                                  String teacherType, List<String> subjectCodes,
                                  List<BusyEntry> busyEntries, List<ExternalSlot> externalSlots) {}

    public record BusyDaysRequest(String schoolId, String teacherId, List<Integer> busyDaysOfWeek, String note) {}

    public record BusyDatesRequest(String schoolId, String teacherId, List<LocalDate> dates, String note) {}

    public record BatchAssignRequest(String schoolId, String teacherId, List<String> classIds) {}

}
