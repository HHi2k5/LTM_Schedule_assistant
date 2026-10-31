package com.dto;

import java.util.List;
import java.util.Map;

public final class CatalogImportDTO {
    private CatalogImportDTO() {}
    public enum Kind { LECTURERS, ROOMS, ASSIGNMENTS }
    public record ImportRow(int rowNumber, String action, Map<String, Object> values,
                            Map<String, Object> previous, List<String> errors) {}
    public record Preview(String schoolCode, Kind kind, String snapshot, int created, int updated,
                          int unchanged, int invalid, boolean canCommit, List<ImportRow> rows) {}
    public record CommitResult(String status, int created, int updated, int unchanged) {}
    public record LegacyLecturer(String lecturerCode, String fullName, String accountTeacherId, String existingScheduleTeacherId) {}
    public record LegacySchool(String schoolCode, List<String> names, long roomCount, boolean registered,
                               boolean supportedCode, List<LegacyLecturer> lecturers) {}
}
