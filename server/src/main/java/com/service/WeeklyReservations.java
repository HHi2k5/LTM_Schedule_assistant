package com.service;

import java.time.*;
import java.util.*;

/** Resource/day indexes and a rollback journal for tentative placements. */
final class WeeklyReservations {

    record Reservation(
            String teacher,
            String school,
            String room,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            String parent,
            String group,
            String courseCode,
            int periodCount,
            boolean practice,
            boolean generated
    ) {
        Reservation(String teacher, String school, String room, LocalDate date, LocalTime start,
                    LocalTime end, String parent, String group, boolean practice, boolean generated) {
            this(teacher, school, room, date, start, end, parent, group, "", 0, practice, generated);
        }
    }

    private record TeacherDay(String teacher, LocalDate date) {}
    private record RoomDay(String school, String room, LocalDate date) {}
    private record StudentDay(String school, String parent, LocalDate date) {}
    private record ClassDay(String school, String classKey, LocalDate date) {}
    private record GroupKey(String school, String group) {}
    private record GeneratedDay(String teacher, boolean practice, DayOfWeek day) {}
    private record TeacherHalfDay(String teacher, LocalDate date, int halfDay) {}

    private final List<Reservation> journal = new ArrayList<>();
    private final Map<TeacherDay, List<Reservation>> teachers = new HashMap<>();
    private final Map<RoomDay, List<Reservation>> rooms = new HashMap<>();
    private final Map<StudentDay, List<Reservation>> students = new HashMap<>();
    private final Map<GeneratedDay, Integer> generatedDays = new HashMap<>();

    private final Map<ClassDay, Integer> theoryClassDayPeriods = new HashMap<>();
    private final Map<GroupKey, List<LocalDate>> practiceGroupDates = new HashMap<>();
    private final Map<TeacherDay, Map<String, Integer>> teacherDayCourses = new HashMap<>();
    private final Map<TeacherHalfDay, Map<String, Integer>> teacherHalfDayCourses = new HashMap<>();

    int size() { return journal.size(); }

    void add(Reservation r) {
        journal.add(r);
        var td = new TeacherDay(r.teacher(), r.date());
        append(teachers, td, r);
        append(rooms, new RoomDay(r.school(), roomKey(r.room()), r.date()), r);
        if (r.parent() != null) {
            append(students, new StudentDay(r.school(), r.parent(), r.date()), r);
        }
        if (r.generated()) {
            generatedDays.merge(new GeneratedDay(r.teacher(), r.practice(), r.date().getDayOfWeek()), 1, Integer::sum);
        }

        if (!r.practice() && r.periodCount() > 0) {
            String key = theoryClassIdentifier(r.parent(), r.group());
            if (key != null) {
                theoryClassDayPeriods.merge(new ClassDay(r.school(), key, r.date()), r.periodCount(), Integer::sum);
            }
        }

        if (r.practice() && r.group() != null && !r.group().isBlank()) {
            practiceGroupDates.computeIfAbsent(new GroupKey(r.school(), r.group()), ignored -> new ArrayList<>()).add(r.date());
        }

        String normalizedCourse = normalizeCourse(r.courseCode());
        if (!normalizedCourse.isEmpty()) {
            teacherDayCourses.computeIfAbsent(td, ignored -> new HashMap<>())
                    .merge(normalizedCourse, 1, Integer::sum);
            for (int half : SchedulingPolicy.occupiedHalfDays(r.start(), r.end())) {
                teacherHalfDayCourses.computeIfAbsent(new TeacherHalfDay(r.teacher(), r.date(), half), ignored -> new HashMap<>())
                        .merge(normalizedCourse, 1, Integer::sum);
            }
        }
    }

    void rollback(int boundary) {
        if (boundary < 0 || boundary > journal.size()) {
            throw new IllegalArgumentException("Invalid reservation rollback boundary.");
        }
        while (journal.size() > boundary) {
            var r = journal.removeLast();
            var td = new TeacherDay(r.teacher(), r.date());
            removeLast(teachers, td);
            removeLast(rooms, new RoomDay(r.school(), roomKey(r.room()), r.date()));
            if (r.parent() != null) {
                removeLast(students, new StudentDay(r.school(), r.parent(), r.date()));
            }
            if (r.generated()) {
                var key = new GeneratedDay(r.teacher(), r.practice(), r.date().getDayOfWeek());
                generatedDays.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
            }
            if (!r.practice() && r.periodCount() > 0) {
                String key = theoryClassIdentifier(r.parent(), r.group());
                if (key != null) {
                    var cd = new ClassDay(r.school(), key, r.date());
                    theoryClassDayPeriods.computeIfPresent(cd, (ignored, total) -> total <= r.periodCount() ? null : total - r.periodCount());
                }
            }
            if (r.practice() && r.group() != null && !r.group().isBlank()) {
                var gk = new GroupKey(r.school(), r.group());
                var dates = practiceGroupDates.get(gk);
                if (dates != null && !dates.isEmpty()) {
                    dates.removeLast();
                    if (dates.isEmpty()) practiceGroupDates.remove(gk);
                }
            }
            String normalizedCourse = normalizeCourse(r.courseCode());
            if (!normalizedCourse.isEmpty()) {
                decrementCourseCount(teacherDayCourses, td, normalizedCourse);
                for (int half : SchedulingPolicy.occupiedHalfDays(r.start(), r.end())) {
                    decrementCourseCount(teacherHalfDayCourses, new TeacherHalfDay(r.teacher(), r.date(), half), normalizedCourse);
                }
            }
        }
    }

    boolean differentSessionDayBlocked(String teacher, LocalDate date, boolean practice) {
        if (generatedDays.containsKey(new GeneratedDay(teacher, !practice, date.getDayOfWeek()))) return true;
        for (var r : teachers.getOrDefault(new TeacherDay(teacher, date), List.of())) {
            if (r.practice() != practice) return true;
        }
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
        if (parent != null) {
            for (var r : students.getOrDefault(new StudentDay(school, parent, date), List.of())) {
                if ((!practice || !r.practice() || Objects.equals(group, r.group())) && overlaps(r, start, end)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean exceedsTheoryDailyLimit(String school, String parent, String group, LocalDate date, int proposedPeriods) {
        String key = theoryClassIdentifier(parent, group);
        int current = key == null ? 0 : theoryClassDayPeriods.getOrDefault(new ClassDay(school, key, date), 0);
        return current + proposedPeriods > SchedulingPolicy.MAX_THEORY_CLASS_PERIODS_PER_DAY;
    }

    int currentTheoryDailyPeriods(String school, String parent, String group, LocalDate date) {
        String key = theoryClassIdentifier(parent, group);
        return key == null ? 0 : theoryClassDayPeriods.getOrDefault(new ClassDay(school, key, date), 0);
    }

    boolean satisfiesPracticeGap(String school, String group, LocalDate candidateDate, int minGapDays) {
        if (group == null || group.isBlank()) return true;
        var existing = practiceGroupDates.getOrDefault(new GroupKey(school, group), List.of());
        return SchedulingPolicy.satisfiesPracticeGap(existing, candidateDate, minGapDays);
    }

    List<LocalDate> practiceDatesOfGroup(String school, String group) {
        if (group == null || group.isBlank()) return List.of();
        return List.copyOf(practiceGroupDates.getOrDefault(new GroupKey(school, group), List.of()));
    }

    int[] evaluateTeacherCourseGrouping(String teacher, LocalDate date, LocalTime start, LocalTime end, String courseCode) {
        String code = normalizeCourse(courseCode);
        var td = new TeacherDay(teacher, date);
        var dayCourses = teacherDayCourses.getOrDefault(td, Map.of());

        boolean hasSameCourseInDay = dayCourses.containsKey(code);
        boolean hasOtherCourseInDay = dayCourses.keySet().stream().anyMatch(k -> !k.equals(code));

        int addsOtherCourseToDay = hasOtherCourseInDay ? 1 : 0;
        int sameCourseBonus = hasSameCourseInDay && !hasOtherCourseInDay ? -1 : 0;

        int halfDayMixed = 0;
        for (int half : SchedulingPolicy.occupiedHalfDays(start, end)) {
            var halfCourses = teacherHalfDayCourses.getOrDefault(new TeacherHalfDay(teacher, date, half), Map.of());
            if (halfCourses.keySet().stream().anyMatch(k -> !k.equals(code))) {
                halfDayMixed++;
            }
        }

        var dayList = new ArrayList<>(teachers.getOrDefault(td, List.of()));
        dayList.add(new Reservation(teacher, "", "", date, start, end, null, null, code, 0, false, true));
        dayList.sort(Comparator.comparing(Reservation::start));

        int switches = 0;
        String prev = null;
        for (var item : dayList) {
            String c = normalizeCourse(item.courseCode());
            if (c.isEmpty()) continue;
            if (prev != null && !prev.equals(c)) {
                switches++;
            }
            prev = c;
        }

        return new int[]{addsOtherCourseToDay, halfDayMixed, switches, sameCourseBonus};
    }

    private String theoryClassIdentifier(String parent, String group) {
        if (group != null && !group.isBlank()) return group;
        if (parent != null && !parent.isBlank()) return parent + ":LT";
        return null;
    }

    private String normalizeCourse(String courseCode) {
        return courseCode == null ? "" : courseCode.trim().toUpperCase(Locale.ROOT);
    }

    private <K> void decrementCourseCount(Map<K, Map<String, Integer>> map, K key, String course) {
        var inner = map.get(key);
        if (inner == null) return;
        inner.computeIfPresent(course, (ignored, c) -> c <= 1 ? null : c - 1);
        if (inner.isEmpty()) map.remove(key);
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