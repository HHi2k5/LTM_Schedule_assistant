package com.dto;

import com.model.InstitutionLecturer.LecturerType;

/** Explicit contracts keep account credentials and JPA relationships out of JSON. */
public final class CatalogDTO {
    private CatalogDTO() {}

    public record InstitutionInput(String schoolCode, String name, Boolean active, Long revision) {}
    public record InstitutionView(String schoolCode, String name, boolean active, long revision) {}
    public record DepartmentInput(String departmentCode, String name, Boolean active, Long revision) {}
    public record DepartmentView(Long id, String schoolCode, String departmentCode, String name, boolean active, long revision) {}
    public record LecturerInput(String lecturerCode, String fullName, Long departmentId, LecturerType lecturerType,
                                String accountTeacherId, Boolean active, Long revision, String existingScheduleTeacherId) {
        public LecturerInput(String lecturerCode, String fullName, Long departmentId, LecturerType lecturerType,
                             String accountTeacherId, Boolean active, Long revision) {
            this(lecturerCode, fullName, departmentId, lecturerType, accountTeacherId, active, revision, null);
        }
    }
    public record LecturerView(Long id, String schoolCode, String lecturerCode, String fullName, Long departmentId,
                               LecturerType lecturerType, String accountTeacherId, boolean active, long revision, String scheduleTeacherId,
                               String accountUsername, boolean autoProvisioned) {}
    public record AccountTarget(Long id, Long revision) {}
    public record AccountBatch(java.util.List<AccountTarget> lecturers) {}
    public record AccountBatchResult(int created, java.util.List<LecturerView> lecturers) {}
    public record AssignmentInput(String courseCode, String courseName, String cohort, Integer theoryWeight,
                                  Integer practiceWeight, Boolean active, Long revision) {}
    public record AssignmentView(Long id, Long lecturerId, String courseCode, String courseName, String cohort,
                                 int theoryWeight, int practiceWeight, boolean active, long revision) {}
    public record RoomInput(String roomCode, String roomName, String roomType, Integer capacity,
                            String building, Boolean active, Long revision) {}
    public record RoomView(Long id, String schoolCode, String roomCode, String roomName, String roomType,
                           int capacity, boolean isLab, String building, boolean active, long revision) {}
}
