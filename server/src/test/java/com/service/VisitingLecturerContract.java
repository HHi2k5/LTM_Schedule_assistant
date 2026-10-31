package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.VisitingLecturerDTO.*;
import com.dto.SchedulingPlanDTO.Detail;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.LecturerBusySlotRepository;
import com.exception.SchedulingPlanConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class VisitingLecturerContract extends SchedulingPlanFixture {
    @Autowired VisitingLecturerService visiting;
    @Autowired LecturerBusySlotRepository busySlots;
    @Autowired FacultyBoardService board;
    LecturerView visitor(String code, String account) {
        var v = catalogs.createLecturer(school, new LecturerInput(code, "Thỉnh giảng " + code, department.id(), LecturerType.VISITING, account, true, null));
        catalogs.createAssignment(school, v.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null)); return v;
    }
    Input input(Detail plan, LecturerView visitor, String timing, List<FixedSession> fixed, List<Availability> busy) throws Exception {
        var options = visiting.options(plan.plan().id());
        return new Input(plan.plan().revision(), plan.draft().summary().id(), options.snapshot(), visitor.id(), "RECEIVE", timing,
                List.of(options.classes().getFirst().classId()), null, fixed, busy);
    }
    Detail apply(Detail p, Input input) throws Exception {
        var preview = visiting.preview(p.plan().id(), input); return visiting.commit(p.plan().id(), new Commit(input, preview.previewToken()));
    }
    List<FixedSession> fixed(Detail p) throws Exception {
        String id = visiting.options(p.plan().id()).classes().getFirst().classId();
        return List.of(new FixedSession(id, CatalogSchedulingTest.START.plusDays(6), room.id(), 1, 3), new FixedSession(id, CatalogSchedulingTest.START.plusDays(13), room.id(), 1, 3));
    }

    @Test void transferKeepsEveryClassHourAndPublishedCalendarUntilTheNewDraftIsConfirmed() throws Exception {
        var p = generate(create(department), lecturer, 6); p = plans.confirm(p.plan().id(), p.plan().revision());
        var oldIds = projection(p.plan().id()).stream().map(TeachingCourse::getId).toList(); var visitor = visitor("VIS01", null); long accounts = teachers.count();
        var command = input(p, visitor, "KEEP", List.of(), List.of()); var preview = visiting.preview(p.plan().id(), command);
        assertTrue(preview.canConfirm(), preview.conflicts().toString()); assertEquals(6, preview.changes().getFirst().beforePeriods()); assertEquals(6, preview.changes().getFirst().afterPeriods());
        assertEquals(accounts, teachers.count()); assertEquals(oldIds, projection(p.plan().id()).stream().map(TeachingCourse::getId).toList());
        var edited = visiting.commit(p.plan().id(), new Commit(command, preview.previewToken()));
        assertEquals(2, edited.versions().size()); assertEquals(1, edited.plan().confirmedVersion()); assertNotNull(edited.draft().visitingChange());
        assertEquals(oldIds, projection(p.plan().id()).stream().map(TeachingCourse::getId).toList());
        assertEquals(6, edited.draft().result().getClassWorkloads().getFirst().getScheduledPeriods());
        assertTrue(edited.draft().result().getScheduledClasses().stream().allMatch(s -> s.getTeacherName().equals(visitor.fullName())));
        var confirmed = plans.confirm(edited.plan().id(), edited.plan().revision()); assertEquals(6, projection(p.plan().id()).stream().mapToInt(TeachingCourse::getNumPeriods).sum());
        assertEquals(visitor.id(), confirmed.draft().result().getCatalogContext().lecturers().stream().filter(i -> i.teacherId().equals(confirmed.draft().result().getScheduledClasses().getFirst().getTeacherId())).findFirst().orElseThrow().lecturerId());
        assertEquals(accounts, teachers.count()); assertEquals(visitor.lecturerCode(), teachers.findById(confirmed.draft().result().getScheduledClasses().getFirst().getTeacherId()).orElseThrow().getUsername());
    }

    @Test void conflictingBusyDraftCanBeSavedButNeverPublishedAndCanBeCorrectedWithoutTouchingThePublishedVersion() throws Exception {
        var p = generate(create(department), lecturer, 6); p = plans.confirm(p.plan().id(), p.plan().revision()); var visitor = visitor("VIS01", null);
        var first = plans.entryPage(p.plan().id(), p.draft().summary().id(), 0).rows().getFirst();
        var busy = List.of(new Availability(first.date(), first.date(), null, first.startTime(), first.endTime(), "Bận tại trường khác"));
        var command = input(p, visitor, "KEEP", List.of(), busy); var preview = visiting.preview(p.plan().id(), command);
        assertTrue(preview.complete()); assertFalse(preview.canConfirm()); assertTrue(preview.conflicts().stream().anyMatch(c -> c.type().equals("BUSY_CONFLICT")));
        var edited = apply(p, command); assertEquals(1, edited.draft().busySlots().size());
        final var draft = edited; assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(draft.plan().id(), draft.plan().revision()));
        assertEquals(1, plans.get(p.plan().id()).plan().confirmedVersion());
        var week = (int) java.time.temporal.ChronoUnit.WEEKS.between(CatalogSchedulingTest.START, first.date().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1;
        var boardData = board.overview(edited.plan().id(), edited.draft().summary().id(), new com.dto.FacultyBoardDTO.Query(week, "export", null, null, null, null, null, null, null, null, null, true, null));
        assertTrue(boardData.entries().stream().flatMap(e -> e.conflicts().stream()).anyMatch(c -> c.type().equals("BUSY_CONFLICT")));
        var corrected = apply(edited, input(edited, visitor, "KEEP", List.of(), List.of())); assertTrue(corrected.draft().busySlots().isEmpty());
        assertEquals(6, corrected.draft().result().getClassWorkloads().getFirst().getScheduledPeriods());
    }

    @Test void fixedSessionsReplaceTheWholeSelectedUnitAndLeaveOtherUnitsUntouched() throws Exception {
        var p = create(department); var row = WeeklyPlanExcelReaderTest.row(); row[5] = 2; row[9] = 6; row[10] = 0;
        p = plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.HEADERS, row}), config(plans.options(p.plan().id()), lecturer.id(), room.id()));
        var visitor = visitor("VIS01", null); var command = input(p, visitor, "FIXED", fixed(p), List.of()); var selectedId = command.classIds().getFirst();
        var original = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(p.draft().summary().id());
        var preview = visiting.preview(p.plan().id(), command); assertTrue(preview.canConfirm(), preview.conflicts().toString()); assertEquals(2, preview.afterEntries().size());
        var edited = apply(p, command); var changed = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(edited.draft().summary().id());
        assertEquals(12, changed.stream().mapToInt(SchedulingEntry::getNumPeriods).sum());
        var expected = original.stream().filter(e -> !e.getScheduleClassId().equals(selectedId)).map(e -> e.getLessonDate() + ":" + e.getStartPeriod() + ":" + e.getRoomCode() + ":" + e.getLecturer().getId()).sorted().toList();
        assertEquals(expected, changed.stream().filter(e -> !e.getScheduleClassId().equals(selectedId)).map(e -> e.getLessonDate() + ":" + e.getStartPeriod() + ":" + e.getRoomCode() + ":" + e.getLecturer().getId()).sorted().toList());
    }

    @Test void newClassAddsExactlyItsOwnDemandAndRejectsDuplicateExistingGroupOrExcessHours() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = visitor("VIS01", null); var options = visiting.options(p.plan().id());
        var newClass = new NewClass("NET", "Networking", "2024", "Lớp TG01", "THEORY", 40, 3, CatalogSchedulingTest.START, CatalogSchedulingTest.START.plusDays(30), 3, null);
        var command = new Input(p.plan().revision(), p.draft().summary().id(), options.snapshot(), visitor.id(), "NEW", "FIXED", List.of(), newClass,
                List.of(new FixedSession("$NEW", CatalogSchedulingTest.START.plusDays(6), room.id(), 1, 3)), List.of());
        var preview = visiting.preview(p.plan().id(), command); assertTrue(preview.canConfirm(), preview.conflicts().toString());
        var excessive = new Input(command.revision(), command.baseVersionId(), command.snapshot(), visitor.id(), "NEW", "FIXED", List.of(), newClass,
                List.of(new FixedSession("$NEW", CatalogSchedulingTest.START.plusDays(6), room.id(), 1, 4)), List.of());
        final var original = p; assertThrows(IllegalArgumentException.class, () -> visiting.preview(original.plan().id(), excessive));
        var duplicate = new NewClass("NET", "Networking", "2024", options.classes().getFirst().classGroup(), "THEORY", 40, 3, CatalogSchedulingTest.START, CatalogSchedulingTest.START.plusDays(30), 3, null);
        assertThrows(IllegalArgumentException.class, () -> visiting.preview(original.plan().id(), new Input(command.revision(), command.baseVersionId(), command.snapshot(), visitor.id(), "NEW", "FIXED", List.of(), duplicate, command.sessions(), List.of())));
        var edited = apply(p, command); assertEquals(2, edited.draft().courses().size()); assertEquals(9, edited.draft().result().getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getRequiredPeriods).sum());
        assertEquals(9, edited.draft().result().getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getScheduledPeriods).sum());
    }

    @Test void publishedBusySlotsBlockOtherSchoolGenerationAndOnlyTheTeacherResource() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = visitor("VIS01", userId);
        var busy = List.of(new Availability(CatalogSchedulingTest.START, CatalogSchedulingTest.START, null, LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59), "Bận cả ngày"));
        p = apply(p, input(p, visitor, "FIXED", fixed(p), busy)); p = plans.confirm(p.plan().id(), p.plan().revision());
        assertEquals(1, busySlots.countActiveForTeacher(userId));
        var request = WeeklyScheduleEngineTest.request(1, 0); request.getTargetSchool().setSchoolId(school + "_EXT");
        request.getCourses().getFirst().setStudyStartDate(CatalogSchedulingTest.START); request.getCourses().getFirst().setStudyEndDate(CatalogSchedulingTest.START);
        request.getCourses().getFirst().setTeachers(List.of(WeeklyScheduleEngineTest.teacher(userId, 1, 0)));
        var externalRoom = Room.builder().schoolCode(school + "_EXT").roomCode("101").roomType("THEORY").capacity(60).build();
        var result = automatic.generateScheduleWithRooms(request, List.of(externalRoom)); assertFalse(result.isSaved());
        assertEquals(6, projection(p.plan().id()).stream().mapToInt(TeachingCourse::getNumPeriods).sum());
        var legacy = SchoolWideScheduleRequest.builder().semesterStartDate(CatalogSchedulingTest.START).totalWeeks(2).targetSchool(request.getTargetSchool())
                .courses(List.of(CourseDemandDTO.builder().courseCode("EXT").courseName("Legacy course").cohort("2024").totalClassesToOpen(1).classSize(50).startWeek(1).numPeriodsPerSession(3).hasPractice(false)
                        .teachers(List.of(WeeklyScheduleEngineTest.teacher(userId, 1, 0))).build())).build();
        var fixedResult = automatic.generateScheduleWithRooms(legacy, List.of(externalRoom)); assertTrue(fixedResult.isSaved());
        assertTrue(fixedResult.getScheduledClasses().stream().noneMatch(s -> s.getDayOfWeek() == 2));
        var book = new WeeklyReservations(); book.add(PublishedBusyReservations.busy(userId, CatalogSchedulingTest.START, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        assertFalse(book.roomBusy(school, "101", CatalogSchedulingTest.START, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        assertFalse(book.differentSessionDayBlocked(userId, CatalogSchedulingTest.START, true));
        assertFalse(book.teacherBusy(userId, CatalogSchedulingTest.START, LocalTime.of(8, 0), LocalTime.of(9, 0)));
        book.rollback(0); assertFalse(book.teacherBusy(userId, CatalogSchedulingTest.START, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        var cleared = apply(p, input(p, visitor, "KEEP", List.of(), List.of())); assertEquals(1, busySlots.countActiveForTeacher(userId));
        plans.confirm(cleared.plan().id(), cleared.plan().revision()); assertEquals(0, busySlots.countActiveForTeacher(userId));
    }

    @Test void staleRevisionRosterOrPreviewTokenCannotCreateAnUnreviewedDraft() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = visitor("VIS01", null); var command = input(p, visitor, "KEEP", List.of(), List.of());
        var preview = visiting.preview(p.plan().id(), command); final var original = p;
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> visiting.commit(original.plan().id(), new Commit(command, "0".repeat(64))));
        catalogs.updateRoom(school, room.id(), new RoomInput("101", "Đã đổi", "THEORY", 70, null, true, room.revision()));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> visiting.commit(original.plan().id(), new Commit(command, preview.previewToken())));
        assertEquals(1, plans.get(p.plan().id()).versions().size()); assertTrue(projection(p.plan().id()).isEmpty());
    }

    @Test void anotherFacultyConfirmationBetweenPreviewAndCommitRequiresFreshReview() throws Exception {
        var p = generate(create(department), lecturer, 6); var other = generate(create(otherDepartment), otherLecturer, 6); var visitor = visitor("VIS01", null);
        var command = input(p, visitor, "KEEP", List.of(), List.of()); var preview = visiting.preview(p.plan().id(), command); assertTrue(preview.canConfirm());
        plans.confirm(other.plan().id(), other.plan().revision());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> visiting.commit(p.plan().id(), new Commit(command, preview.previewToken())));
        assertEquals(1, plans.get(p.plan().id()).versions().size());
    }

    @Test void visitingApiUsesAdminOnlyAndReturnsConcretePreviewBeforeSaving() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = visitor("VIS01", null); var command = input(p, visitor, "KEEP", List.of(), List.of());
        SecurityContextHolder.clearContext(); var root = "/api/admin/scheduling-plans/" + p.plan().id();
        mvc.perform(get(root + "/visiting-options")).andExpect(status().isUnauthorized());
        mvc.perform(get(root + "/visiting-options").header("Authorization", "Bearer " + tokens.issue(userId))).andExpect(status().isForbidden());
        var auth = "Bearer " + tokens.issue(adminId);
        var response = mvc.perform(post(root + "/visiting-lecturers/preview").header("Authorization", auth).contentType("application/json").content(json.writeValueAsString(command)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes[0].beforePeriods").value(6)).andExpect(jsonPath("$.changes[0].afterPeriods").value(6)).andReturn();
        String token = json.readTree(response.getResponse().getContentAsByteArray()).path("previewToken").asText();
        mvc.perform(post(root + "/visiting-lecturers/commit").header("Authorization", auth).contentType("application/json").content(json.writeValueAsString(new Commit(command, token))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft.result.saved").value(false)).andExpect(jsonPath("$.draft.visitingChange.lecturerCode").value("VIS01"));
    }

    @Test void inactiveForeignOrUnassignedLecturersAndAutomaticOverwriteAreRejected() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = catalogs.createLecturer(school, new LecturerInput("EMPTY", "Chưa phân công", department.id(), LecturerType.VISITING, null, true, null));
        var invalid = input(p, visitor, "KEEP", List.of(), List.of()); assertThrows(IllegalArgumentException.class, () -> visiting.preview(p.plan().id(), invalid));
        var valid = visitor("VIS01", null); var edited = apply(p, input(p, valid, "KEEP", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> generate(edited, lecturer, 6));
    }

    @Test void legacyIncompletePracticeGroupsRecoverTheirOwnStudentCountsAndParentIds() throws Exception {
        var current = catalogs.listAssignments(school, lecturer.id()).getFirst();
        catalogs.updateAssignment(school, lecturer.id(), current.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, current.revision()));
        lecturer = catalogs.updateLecturer(school, lecturer.id(), new LecturerInput("GV01", "Nguyễn Văn A", department.id(), LecturerType.STAFF, userId, true, lecturer.revision()));
        var hours = ExcelScheduleService.getDefault12PeriodsWithNoonSlot6();
        calendars.saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(userId).semesterStartDate(CatalogSchedulingTest.START)
                .school(SchoolConfigDTO.builder().schoolId(school).schoolName("Trường kiểm thử").periods(hours).build())
                .courses(List.of(TeachingCourseDTO.builder().courseCode("OLD").courseName("Bận LT").room("OTHER").dayOfWeek(2).startPeriod(1).numPeriods(12).weekSchedule("1").build())).build());
        var lab = catalogs.createRoom(school, new RoomInput("LAB", "LAB", "PRACTICE", 25, null, true, null));
        var p = create(department); var source = plans.options(p.plan().id()); var row = WeeklyPlanExcelReaderTest.row(); row[7] = "10/08/2026"; row[9] = 0; row[10] = 6;
        p = plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.HEADERS, row}), config(source, lecturer.id(), lab.id()));
        assertFalse(p.draft().summary().complete()); assertEquals(0, p.draft().entryCount());
        var old = json.readTree(versions.findById(p.draft().summary().id()).orElseThrow().getResultJson());
        old.path("classWorkloads").forEach(node -> { ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("studentCount"); ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("parentClassId"); });
        entityManager.createNativeQuery("update scheduling_plan_versions set result_json=:value where id=:id").setParameter("value", json.writeValueAsString(old)).setParameter("id", p.draft().summary().id()).executeUpdate(); entityManager.clear();
        var restored = visiting.options(p.plan().id()); assertEquals(2, restored.classes().size());
        assertTrue(restored.classes().stream().allMatch(c -> c.studentCount() == 25));
        assertEquals(1, restored.classes().stream().map(ClassChoice::parentClassId).distinct().count());
    }

    @Test void aNewBusyDeclarationCannotInvalidateAnotherPublishedPlanForTheSameLinkedTeacher() throws Exception {
        var p = generate(create(department), lecturer, 6); var visitor = visitor("VIS01", userId); String externalSchool = school + "_EXT";
        catalogs.createInstitution(new InstitutionInput(externalSchool, "Trường khác", true, null));
        var dept = catalogs.createDepartment(externalSchool, new DepartmentInput("FIT", "Khoa khác", true, null));
        var ext = catalogs.createLecturer(externalSchool, new LecturerInput("EXT01", "Cùng tài khoản", dept.id(), LecturerType.STAFF, userId, true, null));
        catalogs.createAssignment(externalSchool, ext.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        var extRoom = catalogs.createRoom(externalSchool, new RoomInput("101", "Phòng", "THEORY", 60, null, true, null));
        var other = plans.create(new com.dto.SchedulingPlanDTO.Create(externalSchool, dept.id(), "Lịch ngoài", "HK1", CatalogSchedulingTest.START));
        other = plans.generate(other.plan().id(), other.plan().revision(), CatalogSchedulingTest.plan(6, 0), config(plans.options(other.plan().id()), ext.id(), extRoom.id()));
        other = plans.confirm(other.plan().id(), other.plan().revision());
        var occupied = plans.entryPage(other.plan().id(), other.draft().summary().id(), 0).rows().getFirst();
        var beforeIds = projection(other.plan().id()).stream().map(TeachingCourse::getId).sorted().toList();
        var command = input(p, visitor, "FIXED", fixed(p), List.of(new Availability(occupied.date(), occupied.date(), null, occupied.startTime(), occupied.endTime(), "Bận mới")));
        var preview = visiting.preview(p.plan().id(), command); assertFalse(preview.canConfirm()); assertTrue(preview.conflicts().stream().anyMatch(c -> c.type().equals("BUSY_PUBLISHED_CONFLICT")));
        var edited = apply(p, command); assertThrows(SchedulingPlanConflictException.class, () -> plans.confirm(edited.plan().id(), edited.plan().revision()));
        assertEquals(beforeIds, projection(other.plan().id()).stream().map(TeachingCourse::getId).sorted().toList());
        assertEquals(0, busySlots.countActiveForTeacher(userId));
    }
}
