package com.service;

import java.time.*;
import java.util.*;

/** Resource/day indexes and a rollback journal for tentative placements. */
final class WeeklyReservations {
    record Reservation(String teacher, String school, String room, LocalDate date, LocalTime start,
                       LocalTime end, String parent, String group, boolean practice, boolean generated) {}
    private record TeacherDay(String teacher, LocalDate date) {}
    private record RoomDay(String school, String room, LocalDate date) {}
    private record StudentDay(String school, String parent, LocalDate date) {}
    private record GeneratedDay(String teacher, boolean practice, DayOfWeek day) {}

    private final List<Reservation> journal = new ArrayList<>();
    private final Map<TeacherDay, List<Reservation>> teachers = new HashMap<>();
    private final Map<RoomDay, List<Reservation>> rooms = new HashMap<>();
    private final Map<StudentDay, List<Reservation>> students = new HashMap<>();
    private final Map<GeneratedDay, Integer> generatedDays = new HashMap<>();

    int size() { return journal.size(); }

    void add(Reservation r) {
        journal.add(r);
        append(teachers, new TeacherDay(r.teacher(), r.date()), r);
        append(rooms, new RoomDay(r.school(), roomKey(r.room()), r.date()), r);
        if (r.parent() != null) append(students, new StudentDay(r.school(), r.parent(), r.date()), r);
        if (r.generated()) generatedDays.merge(new GeneratedDay(r.teacher(), r.practice(), r.date().getDayOfWeek()), 1, Integer::sum);
    }

    void rollback(int boundary) {
        if (boundary < 0 || boundary > journal.size()) throw new IllegalArgumentException("Invalid reservation rollback boundary.");
        while (journal.size() > boundary) {
            var r = journal.removeLast();
            removeLast(teachers, new TeacherDay(r.teacher(), r.date()));
            removeLast(rooms, new RoomDay(r.school(), roomKey(r.room()), r.date()));
            if (r.parent() != null) removeLast(students, new StudentDay(r.school(), r.parent(), r.date()));
            if (r.generated()) {
                var key = new GeneratedDay(r.teacher(), r.practice(), r.date().getDayOfWeek());
                generatedDays.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
            }
        }
    }

    boolean differentSessionDayBlocked(String teacher, LocalDate date, boolean practice) {
        if (generatedDays.containsKey(new GeneratedDay(teacher, !practice, date.getDayOfWeek()))) return true;
        for (var r : teachers.getOrDefault(new TeacherDay(teacher, date), List.of()))
            if (r.practice() != practice) return true;
        return false;
    }

    boolean teacherBusy(String teacher, LocalDate date, LocalTime start, LocalTime end) {
        return overlaps(teachers.getOrDefault(new TeacherDay(teacher, date), List.of()), start, end);
    }

    boolean roomBusy(String school, String room, LocalDate date, LocalTime start, LocalTime end) {
        return overlaps(rooms.getOrDefault(new RoomDay(school, roomKey(room), date), List.of()), start, end);
    }

    boolean studentsBusy(String school, String parent, String group, boolean practice,
                         LocalDate date, LocalTime start, LocalTime end) {
        for (var r : students.getOrDefault(new StudentDay(school, parent, date), List.of())) {
            if ((!practice || !r.practice() || Objects.equals(group, r.group())) && overlaps(r, start, end)) return true;
        }
        return false;
    }

    private boolean overlaps(List<Reservation> rows, LocalTime start, LocalTime end) {
        for (var r : rows) if (overlaps(r, start, end)) return true;
        return false;
    }
    private boolean overlaps(Reservation r, LocalTime start, LocalTime end) {
        return start.isBefore(r.end()) && r.start().isBefore(end);
    }
    private String roomKey(String room) { return room == null ? "" : room.toUpperCase(Locale.ROOT); }
    private <K> void append(Map<K, List<Reservation>> index, K key, Reservation r) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(r);
    }
    private <K> void removeLast(Map<K, List<Reservation>> index, K key) {
        var rows = index.get(key);
        rows.removeLast();
        if (rows.isEmpty()) index.remove(key);
    }
}
