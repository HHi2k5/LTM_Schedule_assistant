package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.Configuration;
import com.dto.FacultyBoardDTO.*;
import com.dto.SchedulingPlanDTO.Detail;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class FacultyBoardContract extends SchedulingPlanFixture {
    @Autowired FacultyBoardService board;
    Query query(String view, int week, Map<String, Object> filters) {
        var values = new HashMap<String, Object>(filters); values.put("view", view); values.put("week", week); return json.convertValue(values, Query.class);
    }
    Board read(Detail p, String view, int week, Map<String, Object> filters) throws Exception {
        return board.overview(p.plan().id(), p.draft().summary().id(), query(view, week, filters));
    }
    Detail mixed() throws Exception {
        var visiting = catalogs.createLecturer(school, new LecturerInput("GV03", "Giảng viên thỉnh giảng", department.id(), LecturerType.VISITING, null, true, null));
        catalogs.createAssignment(school, visiting.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null));
        catalogs.createAssignment(school, visiting.id(), new AssignmentInput("DB", "Databases", "2025", 1, 0, true, null));
        var a = catalogs.listAssignments(school, lecturer.id()).getFirst();
        catalogs.updateAssignment(school, lecturer.id(), a.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, a.revision()));
        var lab = catalogs.createRoom(school, new RoomInput("LAB", "Lab", "PRACTICE", 30, null, true, null));
        var net = WeeklyPlanExcelReaderTest.row(); net[7] = "16/08/2026"; net[9] = 6; net[10] = 6;
        var db = WeeklyPlanExcelReaderTest.row(); db[0] = 2; db[1] = "DB"; db[2] = "Databases"; db[3] = 20; db[4] = "2025"; db[7] = "16/08/2026"; db[9] = 3; db[10] = 0;
        var p = create(department); var options = plans.options(p.plan().id());
        return plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{ WeeklyPlanExcelReaderTest.HEADERS, net, db }),
                new Configuration(options.snapshot(), List.of(lecturer.id(), visiting.id()), List.of(room.id(), lab.id()), List.of(), options.periodChoices().getFirst().id(), false));
    }

    @Test void boardTabsAndExportUseTheSameSavedWeekAndWorkloadAndDoNotWriteCalendars() throws Exception {
        var p = mixed(); assertTrue(p.draft().summary().complete()); long count = teachers.count();
        var calendar = read(p, "calendar", 1, Map.of()); var assignments = read(p, "assignments", 1, Map.of()); var export = read(p, "export", 1, Map.of());
        assertEquals(calendar.stats(), assignments.stats()); assertEquals(calendar.stats(), export.stats());
        assertEquals(21, export.stats().weekPeriods()); assertEquals(21, export.stats().requiredPeriods()); assertEquals(0, export.stats().missingPeriods());
        assertEquals(4, assignments.assignments().size()); assertTrue(export.entries().stream().anyMatch(e -> "PRACTICE".equals(e.sessionType())));
        assertEquals(export.stats().weekPeriods(), export.entries().stream().mapToInt(Entry::numPeriods).sum());
        assertEquals(export.stats().weekPeriods(), export.assignments().stream().mapToInt(Assignment::weekPeriods).sum());
        assertEquals("DRAFT", export.context().state()); assertEquals(count, teachers.count()); assertTrue(projection(p.plan().id()).isEmpty());
        assertTrue(export.entries().stream().allMatch(e -> !e.date().isBefore(CatalogSchedulingTest.START) && !e.date().isAfter(CatalogSchedulingTest.START.plusDays(6))));
        assertTrue(read(p, "sessions", 2, Map.of()).entries().isEmpty());
    }

    @Test void filtersCombineAndIncludeOnlyTheRequestedLecturerCourseCohortGroupRoomAndSession() throws Exception {
        var p = mixed(); var all = read(p, "export", 1, Map.of());
        var chosen = all.entries().stream().filter(e -> "VISITING".equals(e.lecturerType())).findFirst().orElseThrow();
        var filters = Map.<String, Object>of("lecturerId", chosen.lecturerId(), "courseCode", chosen.courseCode(), "cohort", chosen.cohort(), "classId", chosen.classId(), "roomCode", chosen.roomCode(), "sessionType", chosen.sessionType(), "lecturerType", "VISITING", "q", chosen.courseCode().toLowerCase(Locale.ROOT));
        var filtered = read(p, "export", 1, filters);
        assertFalse(filtered.entries().isEmpty()); assertEquals(1, filtered.assignments().size());
        assertTrue(filtered.entries().stream().allMatch(e -> e.classId().equals(chosen.classId()) && e.lecturerId().equals(chosen.lecturerId()) && e.roomCode().equals(chosen.roomCode())));
        assertEquals(all.options(), filtered.options());
        assertEquals(0, read(p, "calendar", 1, Map.of("q", "does-not-exist")).stats().sessions());
    }

    @Test void wholeTermHoursAreNotPresentedAsWeeklyMissingHoursAndIncompleteDemandsRemainVisible() throws Exception {
        var p = generate(create(department), lecturer, 30);
        var first = read(p, "assignments", 1, Map.of()); assertEquals(30, first.assignments().getFirst().scheduledPeriods()); assertTrue(first.stats().weekPeriods() < 30);
        var last = read(p, "assignments", 100, Map.of()); assertEquals(0, last.stats().sessions()); assertEquals(30, last.stats().scheduledPeriods()); assertEquals(0, last.stats().missingPeriods());
        catalogs.updateRoom(school, room.id(), new RoomInput("101", "Nhỏ", "THEORY", 10, null, true, room.revision()));
        var incomplete = generate(p, lecturer, 6); var report = read(incomplete, "export", 1, Map.of());
        assertTrue(report.entries().isEmpty()); assertEquals(6, report.assignments().getFirst().missingPeriods()); assertFalse(report.context().complete());
    }

    @Test void anotherFacultysReservationIsCheckedBeforeDisplayFilteringAndConfirmedVersionDoesNotConflictWithItself() throws Exception {
        var a = generate(create(department), lecturer, 6); var b = generate(create(otherDepartment), otherLecturer, 6);
        b = plans.confirm(b.plan().id(), b.plan().revision());
        var report = read(a, "export", 1, Map.of("lecturerId", lecturer.id(), "roomCode", "101", "conflictsOnly", true));
        assertFalse(report.entries().isEmpty()); assertTrue(report.entries().stream().allMatch(e -> e.conflicts().stream().anyMatch(c -> c.type().equals("ROOM_CONFLICT"))));
        assertEquals(report.entries().size(), report.stats().conflictingEntries());
        assertEquals("CONFIRMED", read(b, "calendar", 1, Map.of()).context().state());
        assertEquals(0, read(b, "calendar", 1, Map.of()).stats().conflictingEntries());
    }

    @Test void intraVersionConflictsMarkBothSidesEvenWhenOneClassIsHiddenByAFilter() throws Exception {
        var p = generate(create(department), lecturer, 6);
        var original = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(p.draft().summary().id()).getFirst();
        var duplicate = new SchedulingEntry(); BeanUtils.copyProperties(original, duplicate, "id", "entryKey"); duplicate.setScheduleClassId(original.getScheduleClassId() + ":DUP"); entries.saveAndFlush(duplicate);
        var all = read(p, "export", 1, Map.of("conflictsOnly", true));
        assertEquals(Set.of(original.getId(), duplicate.getId()), new HashSet<>(all.entries().stream().map(Entry::id).toList()));
        assertTrue(all.entries().stream().allMatch(e -> e.conflicts().stream().anyMatch(c -> c.type().equals("TEACHER_CONFLICT"))));
        var filtered = read(p, "sessions", 1, Map.of("classId", original.getScheduleClassId(), "conflictsOnly", true));
        assertEquals(1, filtered.entries().size()); assertEquals(original.getId(), filtered.entries().getFirst().id());
    }

    @Test void actualClocksAndAnOlderPersonalAnchorDetectCrossSchoolTeacherClashesWithoutFalseRoomClashes() throws Exception {
        var linked = catalogs.updateLecturer(school, lecturer.id(), new LecturerInput("GV01", "Nguyễn Văn A", department.id(), LecturerType.STAFF, userId, true, lecturer.revision()));
        var p = generate(create(department), linked, 6); var first = read(p, "export", 1, Map.of()).entries().getFirst();
        var hours = p.draft().result().getPeriods();
        calendars.saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(userId).semesterStartDate(CatalogSchedulingTest.START.minusWeeks(1))
                .school(SchoolConfigDTO.builder().schoolId(school + "_OTHER").schoolName("Trường khác").periods(hours).build())
                .courses(List.of(TeachingCourseDTO.builder().courseCode("EXT").courseName("Buổi ngoài trường").room("101").dayOfWeek(first.date().getDayOfWeek().getValue() + 1).startPeriod(1).numPeriods(1)
                        .weekSchedule("-1").customStartTime(first.startTime().plusSeconds(30)).customEndTime(first.endTime().minusSeconds(30)).build())).build());
        var clashes = read(p, "export", 1, Map.of("conflictsOnly", true)); assertFalse(clashes.entries().isEmpty());
        assertTrue(clashes.entries().stream().anyMatch(e -> e.conflicts().stream().anyMatch(c -> c.type().equals("TEACHER_CONFLICT"))));
        assertTrue(clashes.entries().stream().flatMap(e -> e.conflicts().stream()).noneMatch(c -> c.type().equals("ROOM_CONFLICT")));
    }

    @Test void lecturerTypesAreSnapshotsAndOldVersionsReportMissingTypesInsteadOfGuessing() throws Exception {
        var p = generate(create(department), lecturer, 6);
        catalogs.updateLecturer(school, lecturer.id(), new LecturerInput("GV01", "Tên hiện tại khác", department.id(), LecturerType.VISITING, null, true, lecturer.revision()));
        var report = read(p, "export", 1, Map.of()); assertEquals("STAFF", report.entries().getFirst().lecturerType()); assertEquals("Nguyễn Văn A", report.entries().getFirst().lecturerName());
        assertTrue(read(p, "export", 1, Map.of("lecturerType", "VISITING")).entries().isEmpty());
        var old = json.readTree(versions.findById(p.draft().summary().id()).orElseThrow().getResultJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) old.path("catalogContext").path("lecturers").get(0)).remove("lecturerType");
        entityManager.createNativeQuery("update scheduling_plan_versions set result_json=:value where id=:id").setParameter("value", json.writeValueAsString(old)).setParameter("id", p.draft().summary().id()).executeUpdate(); entityManager.clear();
        report = read(p, "export", 1, Map.of()); assertTrue(report.options().missingTypeSnapshot()); assertEquals("UNKNOWN", report.entries().getFirst().lecturerType());
    }

    @Test void groupPaginationIsBoundedAndExportIncludesAllPagesWithCurrentVersionState() throws Exception {
        var p = create(department); var options = plans.options(p.plan().id()); var row = WeeklyPlanExcelReaderTest.row(); row[5] = 11; row[7] = "16/08/2026"; row[9] = 3; row[10] = 0;
        p = plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.HEADERS, row}), config(options, lecturer.id(), room.id()));
        var first = read(p, "calendar", 1, Map.of("groupBy", "class")); var second = read(p, "calendar", 1, Map.of("groupBy", "class", "page", 1));
        assertEquals(11, first.totalRows()); assertEquals(10, first.groups().size()); assertEquals(1, second.groups().size()); assertEquals(2, first.pageCount());
        assertTrue(Collections.disjoint(first.groups().stream().map(Group::id).toList(), second.groups().stream().map(Group::id).toList()));
        assertEquals(11, read(p, "export", 1, Map.of("page", 1)).entries().size());
        var original = p; p = generate(p, lecturer, 6);
        assertEquals("HISTORICAL", read(original, "export", 1, Map.of()).context().state()); assertEquals("DRAFT", read(p, "calendar", 1, Map.of()).context().state());
    }

    @Test void overviewApiEnforcesRoleScopeDefaultsAndInvalidParameters() throws Exception {
        var p = generate(create(department), lecturer, 6); var other = create(otherDepartment);
        SecurityContextHolder.clearContext(); String path = "/api/admin/scheduling-plans/" + p.plan().id() + "/versions/" + p.draft().summary().id() + "/overview";
        mvc.perform(get(path)).andExpect(status().isUnauthorized()); mvc.perform(get(path).header("Authorization", "Bearer " + tokens.issue(userId))).andExpect(status().isForbidden());
        String auth = "Bearer " + tokens.issue(adminId);
        mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.filters.week").value(1)).andExpect(jsonPath("$.groups[0].entries[0].courseCode").value("NET"));
        mvc.perform(get(path).param("week", "0").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("view", "unknown").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("sessionType", "invalid").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get(path.replace("/" + p.plan().id() + "/versions/", "/" + other.plan().id() + "/versions/")).header("Authorization", auth)).andExpect(status().isNotFound());
    }
}
