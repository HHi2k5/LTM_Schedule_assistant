package com.dto;

import com.dto.CatalogDTO.*;
import java.util.List;

public final class CatalogSchedulingDTO {
    private CatalogSchedulingDTO() {}
    public record LecturerOption(LecturerView lecturer, boolean eligible, String reason) {}
    public record PeriodChoice(String id, String label, List<PeriodTimeDTO> periods) {}
    public record Options(InstitutionView school, String snapshot, List<LecturerOption> lecturers,
                          List<AssignmentView> assignments, List<RoomView> rooms, List<PeriodChoice> periodChoices) {}
    public record WeightOverride(Long assignmentId, Integer theoryWeight, Integer practiceWeight) {}
    public record Configuration(String snapshot, List<Long> lecturerIds, List<Long> roomIds,
                                 List<WeightOverride> weights, String periodChoiceId, boolean replaceSchoolSchedule) {}
    public record LecturerIdentity(Long lecturerId, String lecturerCode, String teacherId, String accountTeacherId, String lecturerType) {
        public LecturerIdentity(Long lecturerId, String lecturerCode, String teacherId, String accountTeacherId) {
            this(lecturerId, lecturerCode, teacherId, accountTeacherId, null);
        }
    }
    public record RunContext(String schoolCode, String sourceSnapshot, List<Long> roomIds,
                             List<LecturerIdentity> lecturers, String periodChoiceId) {}
}
