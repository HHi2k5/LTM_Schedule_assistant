package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.SchedulingPlanDTO.*;
import com.exception.SchedulingPlanConflictException;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import org.junit.jupiter.api.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
abstract class SchedulingPlanContract extends SchedulingPlanFixture {
    @Test void savedDraftReopensWithoutCreatingProfilesOrWritingPersonalSchedulesAndConfirmationIsIdempotent() throws Exception {
        long before = teachers.count();
        var created = create(department); var draft = generate(created, lecturer, 6);
        assertTrue(draft.draft().summary().complete()); assertFalse(draft.draft().result().isSaved());
        assertEquals(before, teachers.count()); assertNull(catalogs.listLecturers(school).getFirst().scheduleTeacherId());
        assertTrue(projection(created.plan().id()).isEmpty());
        entityManager.flush(); entityManager.clear();
        var reopened = plans.get(created.plan().id());
        assertEquals(draft.draft().summary().id(), reopened.draft().summary().id());
        assertEquals(6, plans.entryPage(created.plan().id(), draft.draft().summary().id(), 0).rows().stream().mapToInt(Entry::numPeriods).sum());
        assertTrue(plans.validate(created.plan().id(), reopened.plan().revision()).isEmpty());
        var confirmed = plans.confirm(created.plan().id(), reopened.plan().revision());
        assertEquals("CONFIRMED", confirmed.plan().state()); assertTrue(confirmed.draft().result().isSaved());
        assertEquals(before, teachers.count());
        String identity = confirmed.draft().result().getCatalogContext().lecturers().getFirst().teacherId();
        assertEquals(lecturer.accountTeacherId(), identity); assertNotNull(teachers.findById(identity).orElseThrow().getUsername());
        var rows = projection(created.plan().id()); assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(c -> c.getSchedulingEntryId() != null && "PLAN_CONFIRMED".equals(c.getSourceType())));
        assertEquals(confirmed.draft().entryCount(), rows.size());
        assertFalse(calendars.getWeeklySchedule(identity, 1).getLessons().isEmpty());
        assertEquals(confirmed.plan().revision(), plans.confirm(created.plan().id(), confirmed.plan().revision()).plan().revision());
        assertEquals(rows.size(), projection(created.plan().id()).size());
    }

    @Test void newDraftRetainsPublishedVersionAndHistoryUntilItsOwnConfirmation() throws Exception {
        var first = generate(create(department), lecturer, 6);
        var published = plans.confirm(first.plan().id(), first.plan().revision());
        var oldIds = projection(first.plan().id()).stream().map(TeachingCourse::getId).toList();
        var next = generate(published, lecturer, 9);
        assertEquals("DRAFT", next.plan().state()); assertEquals(1, next.plan().confirmedVersion()); assertEquals(2, next.plan().draftVersion());
        assertEquals(oldIds, projection(first.plan().id()).stream().map(TeachingCourse::getId).toList());
        assertEquals(2, next.versions().size());
        assertEquals(6, plans.version(first.plan().id(), first.draft().summary().id()).result().getClassWorkloads().getFirst().getScheduledPeriods());
        var confirmed = plans.confirm(next.plan().id(), next.plan().revision()); entityManager.flush(); entityManager.clear();
        assertEquals(2, confirmed.plan().confirmedVersion()); assertEquals(9, projection(first.plan().id()).stream().mapToInt(TeachingCourse::getNumPeriods).sum());
        assertEquals(first.draft().entryCount(), entries.countByVersion_Id(first.draft().summary().id()));
        assertTrue(plans.version(first.plan().id(), first.draft().summary().id()).summary().confirmedAt() != null);
    }

    @Test void confirmationOfAnotherFacultyInvalidatesConflictingDraftWithoutPublishingIt() throws Exception {
        var a = generate(create(department), lecturer, 6);
        var b = generate(create(otherDepartment), otherLecturer, 6);
        assertTrue(plans.validate(b.plan().id(), b.plan().revision()).isEmpty());
        plans.confirm(a.plan().id(), a.plan().revision());
        var conflicts = plans.validate(b.plan().id(), b.plan().revision());
        assertTrue(conflicts.stream().anyMatch(c -> c.type().equals("ROOM_CONFLICT")), conflicts.toString());
        var error = assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(b.plan().id(), b.plan().revision()));
        assertFalse(error.getConflicts().isEmpty()); assertTrue(projection(b.plan().id()).isEmpty());
        assertFalse(projection(a.plan().id()).isEmpty());
    }

    @Test void regeneratingAndPublishingOneFacultyPreservesOtherFacultyCourseIds() throws Exception {
        var a = generate(create(department), lecturer, 6); a = plans.confirm(a.plan().id(), a.plan().revision());
        var b = generate(create(otherDepartment), otherLecturer, 6); b = plans.confirm(b.plan().id(), b.plan().revision());
        var bIds = projection(b.plan().id()).stream().map(TeachingCourse::getId).toList();
        var next = generate(a, lecturer, 12); assertTrue(plans.validate(a.plan().id(), next.plan().revision()).isEmpty());
        plans.confirm(a.plan().id(), next.plan().revision());
        assertEquals(bIds, projection(b.plan().id()).stream().map(TeachingCourse::getId).toList());
        assertEquals(12, projection(a.plan().id()).stream().mapToInt(TeachingCourse::getNumPeriods).sum());
    }

    @Test void roomChangesAndIncompleteWorkloadBlockConfirmation() throws Exception {
        var draft = generate(create(department), lecturer, 6);
        catalogs.updateRoom(school, room.id(), new RoomInput("101", "Đã giảm sức chứa", "THEORY", 10, null, true, room.revision()));
        assertTrue(plans.validate(draft.plan().id(), draft.plan().revision()).stream().anyMatch(c -> c.type().equals("ROOM_CONFIGURATION")));
        var incomplete = generate(draft, lecturer, 6);
        assertFalse(incomplete.draft().summary().complete()); assertEquals(0, incomplete.draft().entryCount());
        assertTrue(plans.validate(incomplete.plan().id(), incomplete.plan().revision()).stream().anyMatch(c -> c.type().equals("WORKLOAD")));
        assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(incomplete.plan().id(), incomplete.plan().revision()));
        assertNull(catalogs.listLecturers(school).getFirst().scheduleTeacherId());
    }

    @Test void newPersonalCalendarAtAnotherSchoolBlocksConfirmationUsingTheLinkedIdentityAndActualHours() throws Exception {
        var linked = catalogs.updateLecturer(school, lecturer.id(), new LecturerInput("GV01", "Nguyễn Văn A", department.id(), LecturerType.STAFF, userId, true, lecturer.revision()));
        var draft = generate(create(department), linked, 6);
        var slot = draft.draft().result().getScheduledClasses().getFirst();
        var hours = draft.draft().result().getPeriods();
        var start = hours.stream().filter(p -> p.getPeriod() == slot.getStartPeriod()).findFirst().orElseThrow().getStart();
        var end = hours.stream().filter(p -> p.getPeriod() == slot.getStartPeriod() + slot.getNumPeriods() - 1).findFirst().orElseThrow().getEnd();
        calendars.saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(userId).semesterStartDate(CatalogSchedulingTest.START)
                .school(SchoolConfigDTO.builder().schoolId(school + "_EXT").schoolName("Trường khác").periods(hours).build())
                .courses(List.of(TeachingCourseDTO.builder().courseCode("EXT").courseName("Lịch ngoài trường").room("999").dayOfWeek(slot.getDayOfWeek()).startPeriod(slot.getStartPeriod())
                        .numPeriods(slot.getNumPeriods()).weekSchedule(slot.getWeekSchedule()).customStartTime(start).customEndTime(end).build())).build());
        var conflicts = plans.validate(draft.plan().id(), draft.plan().revision());
        assertTrue(conflicts.stream().anyMatch(c -> c.type().equals("TEACHER_CONFLICT")), conflicts.toString());
        assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(draft.plan().id(), draft.plan().revision()));
        assertTrue(projection(draft.plan().id()).isEmpty());
        assertTrue(teachers.findTeacherWithFullSchedule(userId).orElseThrow().getSchools().stream().anyMatch(s -> s.getSchoolCode().equals(school + "_EXT") && !s.getCourses().isEmpty()));
    }

    @Test void facultySelectionAndSnapshotCannotBeForged() throws Exception {
        var created = create(department); var options = plans.options(created.plan().id());
        assertEquals(List.of(lecturer.id()), options.lecturers().stream().map(o -> o.lecturer().id()).toList());
        assertThrows(IllegalArgumentException.class, () -> plans.generate(created.plan().id(), created.plan().revision(), CatalogSchedulingTest.plan(6, 0), config(options, otherLecturer.id(), room.id())));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> plans.generate(created.plan().id(), created.plan().revision(), CatalogSchedulingTest.plan(6, 0), config(catalogScheduler.options(school), lecturer.id(), room.id())));
        assertNull(plans.get(created.plan().id()).draft());
    }

    @Test void stalePlanRevisionDoesNotOverwriteAnotherSavedDraft() throws Exception {
        var created = create(department); var draft = generate(created, lecturer, 6);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> generate(created, lecturer, 9));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> plans.confirm(draft.plan().id(), created.plan().revision()));
        assertEquals(1, plans.get(created.plan().id()).versions().size()); assertTrue(projection(created.plan().id()).isEmpty());
    }

    @Test void legacyWritersCannotRemoveOrReplaceConfirmedPlanProjection() throws Exception {
        var a = generate(create(department), lecturer, 6); a = plans.confirm(a.plan().id(), a.plan().revision());
        String identity = a.draft().result().getScheduledClasses().getFirst().getTeacherId();
        final var published = a;
        assertThrows(IllegalArgumentException.class, () -> automatic.requireSchoolWideWriteAllowed(school.toLowerCase(Locale.ROOT)));
        assertThrows(IllegalArgumentException.class, () -> catalogScheduler.generate(school, CatalogSchedulingTest.START, CatalogSchedulingTest.plan(6, 0),
                new Configuration(catalogScheduler.options(school).snapshot(), List.of(lecturer.id()), List.of(room.id()), List.of(), catalogScheduler.options(school).periodChoices().getFirst().id(), true)));
        assertThrows(IllegalArgumentException.class, () -> calendars.deleteSchoolSchedule(identity, school));
        var input = BackendRegressionTest.upload(); input.setTeacherId(identity); input.getSchool().setSchoolId(school);
        assertThrows(IllegalArgumentException.class, () -> calendars.saveOrUpdateSchoolSchedule(input));
        assertEquals(published.draft().entryCount(), projection(published.plan().id()).size());
    }

    @Test void planApiRequiresAdminAndReturnsStructuredConflictAndRevisionErrors() throws Exception {
        var draft = generate(create(department), lecturer, 6);
        catalogs.updateRoom(school, room.id(), new RoomInput("101", "Ngừng", "THEORY", 60, null, false, room.revision()));
        SecurityContextHolder.clearContext(); String root = "/api/admin/scheduling-plans";
        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(get(root).header("Authorization", "Bearer " + tokens.issue(userId))).andExpect(status().isForbidden());
        String auth = "Bearer " + tokens.issue(adminId);
        mvc.perform(get(root + "/" + draft.plan().id()).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.draft.result.saved").value(false));
        mvc.perform(post(root + "/" + draft.plan().id() + "/confirm").header("Authorization", auth).contentType("application/json").content("{\"revision\":" + draft.plan().revision() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.conflicts[0].type").value("ROOM_CONFIGURATION"));
        mvc.perform(post(root + "/" + draft.plan().id() + "/confirm").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void versionEndpointsRejectAnotherPlansHistoryAndPaginateOccurrences() throws Exception {
        var a = generate(create(department), lecturer, 90); var b = create(otherDepartment);
        assertTrue(a.draft().entryCount() > 25);
        var first = plans.entryPage(a.plan().id(), a.draft().summary().id(), 0);
        var second = plans.entryPage(a.plan().id(), a.draft().summary().id(), 1);
        assertEquals(25, first.rows().size()); assertFalse(second.rows().isEmpty());
        assertTrue(Collections.disjoint(first.rows().stream().map(Entry::id).toList(), second.rows().stream().map(Entry::id).toList()));
        assertThrows(com.exception.ResourceNotFoundException.class, () -> plans.version(b.plan().id(), a.draft().summary().id()));
        assertThrows(com.exception.ResourceNotFoundException.class, () -> plans.entryPage(b.plan().id(), a.draft().summary().id(), 0));
    }
}
