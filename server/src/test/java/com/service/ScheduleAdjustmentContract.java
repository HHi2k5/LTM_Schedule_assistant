package com.service;

import com.dto.ScheduleAdjustmentDTO.*;
import com.dto.SchedulingPlanDTO.Detail;
import com.dto.CatalogDTO.*;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.exception.SchedulingPlanConflictException;
import com.repository.SchedulingChangeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class ScheduleAdjustmentContract extends SchedulingPlanFixture {
    @Autowired ScheduleAdjustmentService adjustments;
    @Autowired SchedulingChangeRepository journal;
    Input command(Detail p, String action, List<Long> ids, List<String> classes, List<Edit> edits, Long actor, Boolean locked, Long restore, boolean override) throws Exception {
        return new Input(p.plan().revision(), p.draft().summary().id(), adjustments.options(p.plan().id()).snapshot(), action, ids, classes, edits, actor, locked,
                null, null, null, restore, override, "Điều chỉnh kiểm thử");
    }
    Detail apply(Detail p, Input input) throws Exception {
        var preview = adjustments.preview(p.plan().id(), input); assertTrue(preview.canApply(), preview.message());
        return adjustments.commit(p.plan().id(), new Commit(input, preview.previewToken()));
    }
    List<SchedulingEntry> rows(Detail p) { return entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(p.draft().summary().id()); }

    @Test void movingOneOccurrenceKeepsKeysHoursOtherOccurrencesAndPublishedCalendar() throws Exception {
        var p = generate(create(department), lecturer, 6); p = plans.confirm(p.plan().id(), p.plan().revision());
        var old = rows(p); var first = old.getFirst(); var calendarIds = projection(p.plan().id()).stream().map(TeachingCourse::getId).toList();
        var input = command(p, "MOVE", List.of(), List.of(), List.of(new Edit(first.getId(), null, first.getLessonDate().plusDays(1), null, null, null)), null, null, null, false);
        long count = versions.count(); var preview = adjustments.preview(p.plan().id(), input);
        assertEquals(count, versions.count()); assertEquals(1, preview.changes().size()); assertEquals(old.size() - 1, preview.unchangedEntries());
        assertEquals(6, preview.beforeRequiredPeriods()); assertEquals(6, preview.afterRequiredPeriods()); assertEquals(0, preview.afterMissingPeriods());
        var edited = apply(p, input); assertTrue(edited.draft().customized()); assertEquals(calendarIds, projection(p.plan().id()).stream().map(TeachingCourse::getId).toList());
        var fresh = rows(edited); assertEquals(old.stream().map(SchedulingEntry::getEntryKey).sorted().toList(), fresh.stream().map(SchedulingEntry::getEntryKey).sorted().toList());
        assertEquals(old.get(1).getLessonDate(), fresh.stream().filter(e -> e.getEntryKey().equals(old.get(1).getEntryKey())).findFirst().orElseThrow().getLessonDate());
        assertEquals(first.getLessonDate(), rows(p).getFirst().getLessonDate());
        assertEquals("MOVE", plans.changeLog(p.plan().id(), 0).rows().getFirst().action());
        assertEquals(adminId, plans.changeLog(p.plan().id(), 0).rows().getFirst().actor());
        assertTrue(plans.validate(edited.plan().id(), edited.plan().revision()).isEmpty());
    }

    @Test void lockedOccurrenceCannotBeMovedDeletedOrRestoredWithoutExplicitOverride() throws Exception {
        var original = generate(create(department), lecturer, 6); var first = rows(original).getFirst();
        var locked = apply(original, command(original, "LOCK", List.of(first.getId()), List.of(), List.of(), null, true, null, false));
        var row = rows(locked).stream().filter(SchedulingEntry::isLocked).findFirst().orElseThrow();
        assertFalse(rows(original).getFirst().isLocked());
        var move = command(locked, "MOVE", List.of(), List.of(), List.of(new Edit(row.getId(), null, row.getLessonDate().plusDays(1), null, null, null)), null, null, null, false);
        var remove = command(locked, "DELETE", List.of(row.getId()), List.of(), List.of(), null, null, null, false);
        var restore = command(locked, "RESTORE", List.of(), List.of(), List.of(), null, null, original.draft().summary().id(), false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(locked.plan().id(), move));
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(locked.plan().id(), remove));
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(locked.plan().id(), restore));
        var restored = apply(locked, command(locked, "RESTORE", List.of(), List.of(), List.of(), null, null, original.draft().summary().id(), true));
        assertEquals(3, restored.versions().size()); assertFalse(restored.draft().customized()); assertTrue(rows(restored).stream().noneMatch(SchedulingEntry::isLocked));
        assertEquals(original.draft().summary().id(), restored.draft().adjustmentChange().restoredVersionId());
    }

    @Test void deletingPreservesDemandAndAddingOnlyMissingHoursMakesDraftCompleteAgain() throws Exception {
        var original = generate(create(department), lecturer, 6); var first = rows(original).getFirst();
        var reduced = apply(original, command(original, "DELETE", List.of(first.getId()), List.of(), List.of(), null, null, null, false));
        assertFalse(reduced.draft().summary().complete()); assertEquals(6, reduced.draft().result().getClassWorkloads().getFirst().getRequiredPeriods());
        assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(reduced.plan().id(), reduced.plan().revision()));
        var add = command(reduced, "ADD", List.of(), List.of(), List.of(new Edit(null, first.getScheduleClassId(), first.getLessonDate(), room.id(), first.getStartPeriod(), first.getNumPeriods())), null, null, null, false);
        var filled = apply(reduced, add); assertTrue(filled.draft().summary().complete()); assertEquals(6, rows(filled).stream().mapToInt(SchedulingEntry::getNumPeriods).sum());
        assertTrue(rows(filled).stream().noneMatch(e -> e.getEntryKey().equals(first.getEntryKey())));
        var excessive = command(filled, "ADD", List.of(), List.of(), List.of(new Edit(null, first.getScheduleClassId(), first.getLessonDate().plusDays(1), room.id(), 1, 1)), null, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(filled.plan().id(), excessive));
    }

    @Test void wholeClassReassignmentSupportsStaffAndRejectsForeignFacultyAndLockedChanges() throws Exception {
        var p = generate(create(department), lecturer, 6); var classId = rows(p).getFirst().getScheduleClassId();
        var staff = catalogs.createLecturer(school, new LecturerInput("STAFF2", "GV cơ hữu", department.id(), LecturerType.STAFF, null, true, null));
        catalogs.createAssignment(school, staff.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        var bad = command(p, "REASSIGN", List.of(), List.of(classId), List.of(), otherLecturer.id(), null, null, false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(p.plan().id(), bad));
        var next = apply(p, command(p, "REASSIGN", List.of(), List.of(classId), List.of(), staff.id(), null, null, false));
        assertTrue(rows(next).stream().allMatch(e -> e.getLecturer().getId().equals(staff.id()))); assertEquals(6, rows(next).stream().mapToInt(SchedulingEntry::getNumPeriods).sum());
        var locked = apply(next, command(next, "LOCK", List.of(rows(next).getFirst().getId()), List.of(), List.of(), null, true, null, false));
        var change = command(locked, "REASSIGN", List.of(), List.of(classId), List.of(), lecturer.id(), null, null, false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(locked.plan().id(), change));
    }

    @Test void automaticRepairFillsMissingHoursWhilePreservingLockedAndUnselectedRows() throws Exception {
        var p = generate(create(department), lecturer, 6); var first = rows(p).getFirst();
        var reduced = apply(p, command(p, "DELETE", List.of(first.getId()), List.of(), List.of(), null, null, null, false));
        var locked = apply(reduced, command(reduced, "LOCK", List.of(rows(reduced).getFirst().getId()), List.of(), List.of(), null, true, null, false));
        var kept = rows(locked).getFirst(); var fixed = kept.getLessonDate();
        var repaired = apply(locked, command(locked, "AUTO", List.of(), List.of(first.getScheduleClassId()), List.of(), null, null, null, false));
        assertEquals(6, rows(repaired).stream().mapToInt(SchedulingEntry::getNumPeriods).sum());
        var unchanged = rows(repaired).stream().filter(e -> e.getEntryKey().equals(kept.getEntryKey())).findFirst().orElseThrow();
        assertTrue(unchanged.isLocked()); assertEquals(fixed, unchanged.getLessonDate()); assertTrue(plans.validate(repaired.plan().id(), repaired.plan().revision()).isEmpty());
    }

    @Test void noSolutionAndNoChangeNeverCreateVersionsOrJournalEntries() throws Exception {
        var p = generate(create(department), lecturer, 6); var row = rows(p).getFirst(); var normal = command(p, "AUTO", List.of(row.getId()), List.of(), List.of(), null, null, null, false);
        var impossible = new Input(normal.revision(), normal.baseVersionId(), normal.snapshot(), normal.action(), normal.entryIds(), normal.classIds(), normal.edits(), null, null,
                CatalogSchedulingTest.START.plusDays(6), CatalogSchedulingTest.START.plusDays(6), List.of(room.id()), null, false, "Chỉ chủ nhật");
        var preview = adjustments.preview(p.plan().id(), impossible); assertEquals("NO_SOLUTION", preview.status()); assertFalse(preview.canApply()); assertTrue(preview.changes().isEmpty());
        long count = versions.count(), logs = journal.count(); assertThrows(IllegalArgumentException.class, () -> adjustments.commit(p.plan().id(), new Commit(impossible, preview.previewToken())));
        var same = command(p, "LOCK", List.of(row.getId()), List.of(), List.of(), null, false, null, false); assertEquals("NO_CHANGE", adjustments.preview(p.plan().id(), same).status());
        assertEquals(count, versions.count()); assertEquals(logs, journal.count());
    }

    @Test void stalePreviewAndForeignEntryAreRejectedWithoutAdditionalVersions() throws Exception {
        var p = generate(create(department), lecturer, 6); var input = command(p, "LOCK", List.of(rows(p).getFirst().getId()), List.of(), List.of(), null, true, null, false);
        var preview = adjustments.preview(p.plan().id(), input); var next = apply(p, input); long count = versions.count();
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> adjustments.commit(p.plan().id(), new Commit(input, preview.previewToken())));
        var bad = command(next, "DELETE", List.of(Long.MAX_VALUE), List.of(), List.of(), null, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> adjustments.preview(p.plan().id(), bad)); assertEquals(count, versions.count());
        assertEquals(0, adjustments.editable(next.plan().id(), next.draft().summary().id(), 0, "%_").total());
        authenticate(userId, "TEACHER"); assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> adjustments.options(p.plan().id()));
    }

    @Test void restoringTheOriginalAlsoRestoresDemandBusyAndAssignmentsWithoutPublishing() throws Exception {
        var original = generate(create(department), lecturer, 6);
        var v = catalogs.createLecturer(school, new LecturerInput("VIS3", "GV thỉnh giảng", department.id(), LecturerType.VISITING, null, true, null));
        catalogs.createAssignment(school, v.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null));
        var service = this.visitingService; var s = service.options(original.plan().id());
        var newClass = new com.dto.VisitingLecturerDTO.NewClass("NET", "Networking", "2024", "Lớp thêm", "THEORY", 40, 3, CatalogSchedulingTest.START, CatalogSchedulingTest.START.plusDays(30), 3, null);
        var input = new com.dto.VisitingLecturerDTO.Input(original.plan().revision(), original.draft().summary().id(), s.snapshot(), v.id(), "NEW", "FIXED", List.of(), newClass,
                List.of(new com.dto.VisitingLecturerDTO.FixedSession("$NEW", CatalogSchedulingTest.START.plusDays(6), room.id(), 1, 3)),
                List.of(new com.dto.VisitingLecturerDTO.Availability(CatalogSchedulingTest.START, CatalogSchedulingTest.START, null, java.time.LocalTime.of(23, 0), java.time.LocalTime.of(23, 30), "Bận")));
        var changed = service.commit(original.plan().id(), new com.dto.VisitingLecturerDTO.Commit(input, service.preview(original.plan().id(), input).previewToken()));
        var restore = command(changed, "RESTORE", List.of(), List.of(), List.of(), null, null, original.draft().summary().id(), false);
        var preview = adjustments.preview(changed.plan().id(), restore); assertEquals(9, preview.beforeRequiredPeriods()); assertEquals(6, preview.afterRequiredPeriods()); assertEquals(1, preview.removedBusy().size());
        var restored = apply(changed, restore); assertTrue(restored.draft().busySlots().isEmpty()); assertNull(restored.draft().visitingChange()); assertNull(restored.plan().confirmedVersion());
        assertEquals(1, restored.draft().courses().size()); assertEquals(6, rows(restored).stream().mapToInt(SchedulingEntry::getNumPeriods).sum()); assertTrue(projection(original.plan().id()).isEmpty());
    }
    @Autowired VisitingLecturerService visitingService;

    @Test void automaticRepairAvoidsPublishedOtherFacultyAndExplicitRoomAndDateBounds() throws Exception {
        var p = generate(create(department), lecturer, 6); var external = generate(create(otherDepartment), otherLecturer, 6); plans.confirm(external.plan().id(), external.plan().revision());
        var second = catalogs.createRoom(school, new RoomInput("202", "Phòng thứ hai", "THEORY", 60, null, true, null));
        var selected = rows(p).getFirst(); var base = command(p, "AUTO", List.of(selected.getId()), List.of(), List.of(), null, null, null, false);
        var window = CatalogSchedulingTest.START.plusDays(2);
        var input = new Input(base.revision(), base.baseVersionId(), base.snapshot(), base.action(), base.entryIds(), base.classIds(), base.edits(), null, null, window, window, List.of(second.id()), null, false, "Phòng và ngày giới hạn");
        var result = apply(p, input); var moved = rows(result).stream().filter(e -> e.getEntryKey().equals(selected.getEntryKey())).findFirst().orElseThrow();
        assertEquals(window, moved.getLessonDate()); assertEquals(second.id(), moved.getRoom().getId());
        var old = rows(p).stream().filter(e -> !e.getEntryKey().equals(selected.getEntryKey())).map(SchedulingEntry::getEntryKey).sorted().toList();
        assertEquals(old, rows(result).stream().filter(e -> !e.getEntryKey().equals(selected.getEntryKey())).map(SchedulingEntry::getEntryKey).sorted().toList());
        assertFalse(projection(external.plan().id()).isEmpty());
    }

    @Test void catalogChangesInvalidatePreviewAndWrongTokensCannotBeCommitted() throws Exception {
        var p = generate(create(department), lecturer, 6); var input = command(p, "LOCK", List.of(rows(p).getFirst().getId()), List.of(), List.of(), null, true, null, false);
        var preview = adjustments.preview(p.plan().id(), input);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> adjustments.commit(p.plan().id(), new Commit(input, "bad-token")));
        catalogs.createRoom(school, new RoomInput("303", "Phòng mới", "THEORY", 60, null, true, null));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> adjustments.commit(p.plan().id(), new Commit(input, preview.previewToken())));
        assertEquals(1, plans.get(p.plan().id()).versions().size());
    }

    @Test void httpEndpointsEnforceRolesScopeAndPreviewBeforeCommit() throws Exception {
        var p = generate(create(department), lecturer, 6); var input = command(p, "LOCK", List.of(rows(p).getFirst().getId()), List.of(), List.of(), null, true, null, false);
        String path = "/api/admin/scheduling-plans/" + p.plan().id(), admin = "Bearer " + tokens.issue(adminId), user = "Bearer " + tokens.issue(userId);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path + "/adjustment-options").header("Authorization", user)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path + "/adjustment-entries").param("baseVersionId", p.draft().summary().id().toString()).param("page", "-1").header("Authorization", admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/adjustments/preview").header("Authorization", admin).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.action").value("LOCK"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/adjustments/commit").header("Authorization", admin).contentType("application/json").content(json.writeValueAsString(new Commit(input, null))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path + "/changes").header("Authorization", admin)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1));
    }

    @Test void newPublishedReservationsInvalidateAnAlreadyReviewedAdjustment() throws Exception {
        var p = generate(create(department), lecturer, 6); var external = generate(create(otherDepartment), otherLecturer, 6);
        var input = command(p, "LOCK", List.of(rows(p).getFirst().getId()), List.of(), List.of(), null, true, null, false);
        var preview = adjustments.preview(p.plan().id(), input); assertTrue(preview.canConfirm());
        plans.confirm(external.plan().id(), external.plan().revision());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> adjustments.commit(p.plan().id(), new Commit(input, preview.previewToken())));
        assertEquals(1, plans.get(p.plan().id()).versions().size());
    }

    @Test void automaticRepairRespectsOwnBusyWindowsAndLeavesThemInTheNewVersion() throws Exception {
        var p = generate(create(department), lecturer, 6); var first = rows(p).getFirst();
        var v = catalogs.createLecturer(school, new LecturerInput("VIS4", "GV bận", department.id(), LecturerType.VISITING, null, true, null));
        catalogs.createAssignment(school, v.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        var source = visitingService.options(p.plan().id());
        var receive = new com.dto.VisitingLecturerDTO.Input(p.plan().revision(), p.draft().summary().id(), source.snapshot(), v.id(), "RECEIVE", "KEEP", List.of(first.getScheduleClassId()), null, List.of(),
                List.of(new com.dto.VisitingLecturerDTO.Availability(first.getLessonDate(), first.getLessonDate(), null, java.time.LocalTime.MIN, java.time.LocalTime.of(23, 59, 59), "Bận cả ngày")));
        var changed = visitingService.commit(p.plan().id(), new com.dto.VisitingLecturerDTO.Commit(receive, visitingService.preview(p.plan().id(), receive).previewToken()));
        var command = command(changed, "AUTO", List.of(), List.of(first.getScheduleClassId()), List.of(), null, null, null, false);
        var preview = adjustments.preview(changed.plan().id(), command); assertTrue(preview.canConfirm(), preview.conflicts().toString());
        var fixed = apply(changed, command); assertTrue(rows(fixed).stream().noneMatch(e -> e.getLessonDate().equals(first.getLessonDate())));
        assertEquals(1, fixed.draft().busySlots().size()); assertEquals(6, rows(fixed).stream().mapToInt(SchedulingEntry::getNumPeriods).sum());
    }

    @Test void restoringOriginalAutomaticSettingsWorksEvenWhenAllOccurrencesAlreadyMatch() throws Exception {
        var original = generate(create(department), lecturer, 6);
        var locked = apply(original, command(original, "LOCK", List.of(rows(original).getFirst().getId()), List.of(), List.of(), null, true, null, false));
        var unlocked = apply(locked, command(locked, "LOCK", List.of(rows(locked).stream().filter(SchedulingEntry::isLocked).findFirst().orElseThrow().getId()), List.of(), List.of(), null, false, null, false));
        assertTrue(unlocked.draft().customized());
        var restore = command(unlocked, "RESTORE", List.of(), List.of(), List.of(), null, null, original.draft().summary().id(), false);
        var preview = adjustments.preview(unlocked.plan().id(), restore); assertTrue(preview.changes().isEmpty()); assertTrue(preview.configurationChanged()); assertTrue(preview.canApply());
        var restored = apply(unlocked, restore); assertFalse(restored.draft().customized()); assertEquals(4, restored.versions().size());
        assertEquals(5, generate(restored, lecturer, 6).versions().size());
    }
}
