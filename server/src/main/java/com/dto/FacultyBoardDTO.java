package com.dto;

import java.time.*;
import java.util.*;

public final class FacultyBoardDTO {
    private FacultyBoardDTO() {}
    public record Query(Integer week, String view, String groupBy, Integer page, Long lecturerId, String courseCode,
                        String cohort, String classId, String roomCode, String sessionType, String lecturerType, Boolean conflictsOnly, String q) {
        public Query {
            week = week == null ? 1 : week; page = page == null ? 0 : page;
            view = clean(view, 20); if (view.isEmpty()) view = "calendar";
            groupBy = clean(groupBy, 20); if (groupBy.isEmpty()) groupBy = "lecturer";
            courseCode = clean(courseCode, 50); cohort = clean(cohort, 50); classId = clean(classId, 255);
            roomCode = clean(roomCode, 50); sessionType = clean(sessionType, 20); lecturerType = clean(lecturerType, 20);
            q = clean(q, 200); conflictsOnly = Boolean.TRUE.equals(conflictsOnly);
            if (week < 1 || week > 100 || page < 0 || (lecturerId != null && lecturerId <= 0)
                    || !Set.of("calendar", "assignments", "sessions", "export").contains(view)
                    || !Set.of("lecturer", "class").contains(groupBy)
                    || !Set.of("", "THEORY", "PRACTICE").contains(sessionType)
                    || !Set.of("", "STAFF", "VISITING", "UNKNOWN").contains(lecturerType))
                throw new IllegalArgumentException("Tuần, trang hoặc bộ lọc bảng toàn khoa không hợp lệ.");
        }
        public static Query defaults() { return new Query(null, null, null, null, null, null, null, null, null, null, null, null, null); }
    }
    private static String clean(String value, int max) {
        if (value == null) return "";
        value = value.strip(); if (value.length() > max) throw new IllegalArgumentException("Bộ lọc quá dài."); return value;
    }
    public record Context(Long planId, Long versionId, int versionNumber, String state, boolean complete, long revision,
                          String name, String schoolCode, String schoolName, String departmentName, String semester,
                          LocalDate semesterStartDate, OffsetDateTime updatedAt, Instant checkedAt) {}
    public record Choice(String value, String label) {}
    public record Options(List<Choice> lecturers, List<Choice> courses, List<Choice> cohorts, List<Choice> classes, List<Choice> rooms, boolean missingTypeSnapshot) {}
    public record Issue(String type, String message) {}
    public record Entry(Long id, String classId, String parentClassId, Long lecturerId, String lecturerCode, String lecturerName,
                        String lecturerType, String courseCode, String courseName, String cohort, String classGroup,
                        String sessionType, int studentCount, String roomCode, LocalDate date, LocalTime startTime,
                        LocalTime endTime, int startPeriod, int numPeriods, List<Issue> conflicts, String entryKey, boolean locked) {}
    public record Assignment(String classId, String courseCode, String courseName, String cohort, String classGroup, String sessionType,
                             Long lecturerId, String lecturerCode, String lecturerName, String lecturerType,
                             int requiredPeriods, int scheduledPeriods, int missingPeriods, int weekPeriods, int weekSessions, long conflictingEntries) {}
    public record Group(String id, String label, String subtitle, int totalEntries, boolean truncated, List<Entry> entries) {}
    public record Stats(int sessions, int weekPeriods, long lecturers, int classes, long conflictingEntries, int requiredPeriods, int scheduledPeriods, int missingPeriods) {}
    public record Board(Context context, Query filters, LocalDate startDate, LocalDate endDate, Stats stats, Options options,
                        int page, int pageCount, int totalRows, List<Group> groups, List<Assignment> assignments, List<Entry> entries, List<String> warnings) {}
}
