package com.service;

import com.dto.*;
import com.dto.FacultyBoardDTO.*;
import com.dto.CatalogSchedulingDTO.LecturerIdentity;
import com.exception.ResourceNotFoundException;
import com.model.*;
import com.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class FacultyBoardService {
    private final SchedulingPlanRepository plans;
    private final SchedulingPlanVersionRepository versions;
    private final SchedulingEntryRepository entries;
    private final InstitutionLecturerRepository lecturers;
    private final BoardReservationRepository reservations;
    private final ObjectMapper json;
    private final PublishedBusyReservations publishedBusy;
    private record Actor(Long id, String code, String name, String type, String key) {}
    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::date).thenComparing(Entry::startTime).thenComparing(Entry::id);

    public Board overview(Long planId, Long versionId, Query query) throws Exception {
        SchedulingPlanService.admin();
        var plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Bản lịch không tồn tại."));
        var version = versions.findByIdAndPlan_Id(versionId, planId).orElseThrow(() -> new ResourceNotFoundException("Phiên bản không thuộc bản lịch."));
        var request = json.readValue(version.getRequestJson(), SchoolWideScheduleRequest.class);
        // The immutable draft contains the original actor/type snapshots even after publication.
        var result = json.readValue(version.getResultJson(), AutoScheduleResponse.class);
        var actors = actors(request, result);
        var monday = plan.getSemesterStartDate().plusWeeks(query.week() - 1L); var sunday = monday.plusDays(6);
        var week = entries.findWeek(versionId, monday, sunday);
        var issues = conflicts(plan, week, actors, monday, sunday, request);
        var rows = week.stream().map(e -> entry(e, request, actors, issues.byEntry.getOrDefault(e.getId(), new LinkedHashMap<>()).values().stream().toList())).sorted(ORDER).toList();
        var selected = rows.stream().filter(e -> matches(query, e.lecturerId(), e.courseCode(), e.cohort(), e.classId(), e.sessionType(), e.lecturerType(),
                e.courseCode() + " " + e.courseName() + " " + e.cohort() + " " + e.classGroup() + " " + e.lecturerCode() + " " + e.lecturerName()))
                .filter(e -> query.roomCode().isEmpty() || query.roomCode().equalsIgnoreCase(e.roomCode()))
                .filter(e -> !query.conflictsOnly() || !e.conflicts().isEmpty()).toList();
        var perClass = selected.stream().collect(Collectors.groupingBy(Entry::classId));
        var assignments = new ArrayList<Assignment>();
        var byKey = new HashMap<String, Actor>(); result.getCatalogContext().lecturers().forEach(a -> byKey.put(a.teacherId(), actors.get(a.lecturerId())));
        for (var w : result.getClassWorkloads()) {
            var course = request.getCourses().get(w.getDemandIndex() - 1); var actor = byKey.get(w.getTeacherId());
            var assigned = perClass.getOrDefault(w.getClassId(), List.of());
            Long lecturerId = actor == null ? null : actor.id(); String type = actor == null ? "UNKNOWN" : actor.type();
            String code = actor == null ? "" : actor.code(), name = actor == null ? "Chưa phân công" : actor.name();
            if (!matches(query, lecturerId, w.getCourseCode(), w.getCohort(), w.getClassId(), w.getSessionType(), type,
                    w.getCourseCode() + " " + course.getCourseName() + " " + w.getCohort() + " " + w.getClassGroup() + " " + code + " " + name)) continue;
            // A room or conflict filter is a filter on actual occurrences in this week.
            if ((!query.roomCode().isEmpty() || query.conflictsOnly()) && assigned.isEmpty()) continue;
            assignments.add(new Assignment(w.getClassId(), w.getCourseCode(), course.getCourseName(), w.getCohort(), w.getClassGroup(), w.getSessionType(),
                    lecturerId, code, name, type, w.getRequiredPeriods(), w.getScheduledPeriods(), w.getMissingPeriods(),
                    assigned.stream().mapToInt(Entry::numPeriods).sum(), assigned.size(), assigned.stream().filter(e -> !e.conflicts().isEmpty()).count()));
        }
        assignments.sort(Comparator.comparing(Assignment::courseCode).thenComparing(Assignment::cohort).thenComparing(Assignment::classGroup).thenComparing(Assignment::classId));
        var stats = new Stats(selected.size(), selected.stream().mapToInt(Entry::numPeriods).sum(), selected.stream().map(Entry::lecturerId).distinct().count(), assignments.size(),
                selected.stream().filter(e -> !e.conflicts().isEmpty()).count(), assignments.stream().mapToInt(Assignment::requiredPeriods).sum(), assignments.stream().mapToInt(Assignment::scheduledPeriods).sum(), assignments.stream().mapToInt(Assignment::missingPeriods).sum());
        String state = plan.getConfirmedVersion() != null && plan.getConfirmedVersion().getId().equals(versionId) ? "CONFIRMED"
                : plan.getDraftVersion() != null && plan.getDraftVersion().getId().equals(versionId) ? "DRAFT" : "HISTORICAL";
        var context = new Context(planId, versionId, version.getVersionNumber(), state, version.isComplete(), plan.getRevision(), plan.getName(), plan.getInstitution().getSchoolCode(), plan.getInstitution().getName(),
                plan.getDepartment().getName(), plan.getSemesterKey(), plan.getSemesterStartDate(), plan.getUpdatedAt().atOffset(ZoneOffset.UTC), Instant.now());
        var options = options(request, result, actors);
        var groups = group(selected, query.groupBy());
        int size = "calendar".equals(query.view()) ? 10 : 25;
        int total = "calendar".equals(query.view()) ? groups.size() : "assignments".equals(query.view()) ? assignments.size() : selected.size();
        int pages = (total + size - 1) / size;
        // Export is explicit and includes every matching row, independent of the visible page.
        boolean export = "export".equals(query.view());
        return new Board(context, query, monday, sunday, stats, options, query.page(), export ? 1 : pages, total,
                "calendar".equals(query.view()) ? slice(groups, query.page(), size) : List.of(),
                export ? assignments : "assignments".equals(query.view()) ? slice(assignments, query.page(), size) : List.of(),
                export ? selected : "sessions".equals(query.view()) ? slice(selected, query.page(), size) : List.of(), issues.warnings);
    }

    private Map<Long, Actor> actors(SchoolWideScheduleRequest request, AutoScheduleResponse result) {
        var profiles = new HashMap<Long, InstitutionLecturer>();
        lecturers.findAllById(result.getCatalogContext().lecturers().stream().map(LecturerIdentity::lecturerId).toList()).forEach(l -> profiles.put(l.getId(), l));
        var names = new HashMap<Long, String>(); request.getCourses().forEach(c -> c.getTeachers().forEach(t -> names.put(t.getCatalogLecturerId(), t.getTeacherName())));
        var actors = new LinkedHashMap<Long, Actor>();
        for (var source : result.getCatalogContext().lecturers()) {
            var profile = profiles.get(source.lecturerId()); String key = source.teacherId();
            if (key.startsWith("DRAFT_") && profile != null) {
                if (profile.getAccount() != null) key = profile.getAccount().getTeacherId();
                else if (profile.getScheduleTeacher() != null) key = profile.getScheduleTeacher().getTeacherId();
            }
            actors.put(source.lecturerId(), new Actor(source.lecturerId(), source.lecturerCode(), names.getOrDefault(source.lecturerId(), source.lecturerCode()),
                    source.lecturerType() == null ? "UNKNOWN" : source.lecturerType(), key));
        }
        return actors;
    }

    private static Entry entry(SchedulingEntry e, SchoolWideScheduleRequest request, Map<Long, Actor> actors, List<Issue> issues) {
        var actor = actors.get(e.getLecturer().getId());
        return new Entry(e.getId(), e.getScheduleClassId(), e.getScheduleParentId(), actor.id(), actor.code(), e.getLecturerName(), actor.type(), e.getDemand().getCourseCode(),
                request.getCourses().get(e.getDemand().getDemandIndex() - 1).getCourseName(), e.getDemand().getCohort(), e.getClassGroup(), e.getSessionType(), e.getStudentCount(), e.getRoomCode(),
                e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getStartPeriod(), e.getNumPeriods(), issues, e.getEntryKey(), e.isLocked());
    }

    private static boolean matches(Query q, Long actor, String course, String cohort, String classId, String session, String type, String search) {
        return (q.lecturerId() == null || q.lecturerId().equals(actor)) && (q.courseCode().isEmpty() || q.courseCode().equalsIgnoreCase(course))
                && (q.cohort().isEmpty() || q.cohort().equalsIgnoreCase(cohort)) && (q.classId().isEmpty() || q.classId().equals(classId))
                && (q.sessionType().isEmpty() || q.sessionType().equals(session)) && (q.lecturerType().isEmpty() || q.lecturerType().equals(type))
                && (q.q().isEmpty() || search.toLowerCase(Locale.ROOT).contains(q.q().toLowerCase(Locale.ROOT)));
    }

    private static Options options(SchoolWideScheduleRequest request, AutoScheduleResponse result, Map<Long, Actor> actors) {
        var courses = new TreeMap<String, String>(); var cohorts = new TreeMap<String, String>(); var classes = new TreeMap<String, String>(); var rooms = new TreeMap<String, String>();
        request.getCourses().forEach(c -> { courses.put(c.getCourseCode(), c.getCourseCode() + " · " + c.getCourseName()); cohorts.put(c.getCohort(), c.getCohort()); });
        result.getClassWorkloads().forEach(w -> classes.put(w.getClassId(), w.getCourseCode() + " / " + w.getCohort() + " · " + w.getClassGroup() + " · " + ("PRACTICE".equals(w.getSessionType()) ? "TH" : "LT")));
        result.getScheduledClasses().forEach(e -> rooms.put(e.getRoom(), e.getRoom()));
        return new Options(actors.values().stream().map(a -> new Choice(a.id().toString(), a.code() + " · " + a.name())).toList(), choices(courses), choices(cohorts), choices(classes), choices(rooms), actors.values().stream().anyMatch(a -> "UNKNOWN".equals(a.type())));
    }
    private static List<Choice> choices(Map<String, String> values) { return values.entrySet().stream().map(e -> new Choice(e.getKey(), e.getValue())).toList(); }
    private static <T> List<T> slice(List<T> rows, int page, int size) {
        long from = (long) page * size; return from >= rows.size() ? List.of() : rows.subList((int) from, (int) Math.min(from + size, rows.size()));
    }
    private static List<Group> group(List<Entry> rows, String by) {
        var groups = new TreeMap<String, List<Entry>>(); rows.forEach(e -> groups.computeIfAbsent("class".equals(by) ? e.classId() : e.lecturerId().toString(), ignored -> new ArrayList<>()).add(e));
        return groups.entrySet().stream().map(g -> {
            var e = g.getValue().getFirst(); boolean byClass = "class".equals(by);
            return new Group(g.getKey(), byClass ? e.courseCode() + " · " + e.classGroup() : e.lecturerCode() + " · " + e.lecturerName(),
                    byClass ? "Khóa " + e.cohort() : e.lecturerType(), g.getValue().size(), g.getValue().size() > 200, g.getValue().subList(0, Math.min(200, g.getValue().size())));
        }).sorted(Comparator.comparing(Group::label).thenComparing(Group::id)).toList();
    }

    private static class Issues {
        final Map<Long, LinkedHashMap<String, Issue>> byEntry = new HashMap<>(); final List<String> warnings = new ArrayList<>();
        void add(SchedulingEntry e, String type, String message) { byEntry.computeIfAbsent(e.getId(), ignored -> new LinkedHashMap<>()).putIfAbsent(type, new Issue(type, message)); }
        void warning() { if (warnings.isEmpty()) warnings.add("Có lịch ngoài bản lịch không quy đổi được ngày/giờ. Cần đối chiếu trước khi kết luận không trùng."); }
    }
    private Issues conflicts(SchedulingPlan plan, List<SchedulingEntry> week, Map<Long, Actor> actors, LocalDate monday, LocalDate sunday, SchoolWideScheduleRequest request) {
        var issues = new Issues(); if (week.isEmpty()) return issues;
        var occupied = new WeeklyReservations();
        var busySlots = new WeeklyReservations(); publishedBusy.reservations(plan.getId(), monday, sunday).forEach(busySlots::add);
        PublishedBusyReservations.own(request).stream().filter(b -> !b.date().isBefore(monday) && !b.date().isAfter(sunday) && actors.containsKey(b.lecturerId())).forEach(b -> busySlots.add(PublishedBusyReservations.busy(actors.get(b.lecturerId()).key(), b.date(), b.startTime(), b.endTime())));
        var ids = actors.values().stream().map(Actor::key).distinct().toList();
        for (var c : reservations.findRelevant(plan.getId(), plan.getInstitution().getSchoolCode().toUpperCase(Locale.ROOT), ids, monday, sunday)) {
            var school = c.getSchool(); var teacher = school.getTeacher(); var anchor = teacher.getSemesterStartDate();
            if (anchor == null || anchor.getDayOfWeek() != DayOfWeek.MONDAY || c.getWeekSchedule() == null || c.getDayOfWeek() < 2 || c.getDayOfWeek() > 8) { issues.warning(); continue; }
            long index = Math.floorDiv(ChronoUnit.DAYS.between(anchor, monday), 7);
            if (index < 0 || index >= c.getWeekSchedule().length() || "- ".indexOf(c.getWeekSchedule().charAt((int) index)) >= 0) continue;
            try {
                LocalTime start = c.getCustomStartTime(), end = c.getCustomEndTime();
                if (start == null || end == null) {
                    var periods = ScheduleValidation.periods(school.getPeriods().stream().map(p -> new PeriodTimeDTO(p.getPeriodNumber(), p.getStartTime(), p.getEndTime())).toList());
                    if (!ScheduleValidation.hasPeriods(periods, c.getStartPeriod(), c.getNumPeriods())) { issues.warning(); continue; }
                    if (start == null) start = periods.get(c.getStartPeriod()).getStart(); if (end == null) end = periods.get(c.getStartPeriod() + c.getNumPeriods() - 1).getEnd();
                }
                if (!start.isBefore(end)) { issues.warning(); continue; }
                occupied.add(new WeeklyReservations.Reservation(teacher.getTeacherId(), school.getSchoolCode(), c.getClassroom(), monday.plusDays(c.getDayOfWeek() - 2), start, end,
                        c.getScheduleParentId(), c.getScheduleClassId(), "PRACTICE".equals(c.getSessionType()) || c.getCourseName().startsWith("[TH]"), false));
            } catch (IllegalArgumentException ex) { issues.warning(); }
        }
        String school = plan.getInstitution().getSchoolCode();
        for (var e : week) {
            String teacher = actors.get(e.getLecturer().getId()).key(); boolean practice = "PRACTICE".equals(e.getSessionType());
            if (occupied.teacherBusy(teacher, e.getLessonDate(), e.getStartTime(), e.getEndTime())) issues.add(e, "TEACHER_CONFLICT", "Giảng viên trùng lịch đang áp dụng ngoài bản lịch này.");
            if (busySlots.teacherBusy(teacher, e.getLessonDate(), e.getStartTime(), e.getEndTime())) issues.add(e, "BUSY_CONFLICT", "Buổi dạy trùng lịch bận của giảng viên.");
            if (occupied.roomBusy(school, e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime())) issues.add(e, "ROOM_CONFLICT", "Phòng trùng lịch đang áp dụng của khoa khác hoặc lịch cá nhân.");
            if (occupied.studentsBusy(school, e.getScheduleParentId(), e.getScheduleClassId(), practice, e.getLessonDate(), e.getStartTime(), e.getEndTime())) issues.add(e, "CLASS_CONFLICT", "Lớp/nhóm trùng lịch đang áp dụng ngoài bản lịch.");
            if (occupied.differentSessionDayBlocked(teacher, e.getLessonDate(), practice)) issues.add(e, "SESSION_DAY", "Giảng viên có lịch LT và TH cùng ngày.");
        }
        markPairs(week, e -> actors.get(e.getLecturer().getId()).key() + ":" + e.getLessonDate(), (a,b) -> true, "TEACHER_CONFLICT", "Giảng viên trùng buổi trong phiên bản.", issues);
        markPairs(week, e -> e.getRoomCode().toUpperCase(Locale.ROOT) + ":" + e.getLessonDate(), (a,b) -> true, "ROOM_CONFLICT", "Phòng trùng buổi trong phiên bản.", issues);
        markPairs(week, e -> e.getScheduleParentId() + ":" + e.getLessonDate(), (a,b) -> !"PRACTICE".equals(a.getSessionType()) || !"PRACTICE".equals(b.getSessionType()) || a.getScheduleClassId().equals(b.getScheduleClassId()), "CLASS_CONFLICT", "Lớp/nhóm trùng buổi trong phiên bản.", issues);
        var teacherDays = week.stream().collect(Collectors.groupingBy(e -> actors.get(e.getLecturer().getId()).key() + ":" + e.getLessonDate()));
        teacherDays.values().forEach(day -> { if (day.stream().map(SchedulingEntry::getSessionType).distinct().count() > 1) day.forEach(e -> issues.add(e, "SESSION_DAY", "Giảng viên có LT và TH cùng ngày trong phiên bản.")); });
        return issues;
    }
    private static void markPairs(List<SchedulingEntry> rows, Function<SchedulingEntry, String> key, BiPredicate<SchedulingEntry, SchedulingEntry> related, String type, String message, Issues issues) {
        var groups = rows.stream().collect(Collectors.groupingBy(key));
        for (var group : groups.values()) {
            group.sort(Comparator.comparing(SchedulingEntry::getStartTime));
            for (int i = 0; i < group.size(); i++) for (int j = i + 1; j < group.size(); j++) {
                var a = group.get(i); var b = group.get(j); if (!b.getStartTime().isBefore(a.getEndTime())) break;
                if (related.test(a,b)) { issues.add(a, type, message); issues.add(b, type, message); }
            }
        }
    }
}
