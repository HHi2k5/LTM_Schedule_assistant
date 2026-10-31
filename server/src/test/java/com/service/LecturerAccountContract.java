package com.service;

import com.dto.*;
import com.dto.CatalogDTO.*;
import com.dto.CatalogImportDTO.Kind;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.InstitutionLecturerRepository;
import com.security.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class LecturerAccountContract extends SchedulingPlanFixture {
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired CatalogImportService imports;
    @Autowired InstitutionLecturerRepository roster;
    @Autowired ScheduleAdjustmentService adjustments;
    String code() { return school + "_AUTO"; }
    LecturerInput input(String code, String name, String account, Boolean active, Long revision) {
        return new LecturerInput(code, name, department.id(), LecturerType.STAFF, account, active, revision);
    }
    LecturerView legacy(String code, Teacher identity) {
        var row = catalogs.createLecturer(school, input(code, "Đặng Thị Mỹ Lệ", null, false, null));
        var entity = roster.findById(row.id()).orElseThrow(); entity.setActive(true); entity.setScheduleTeacher(identity == null ? null : teachers.getReferenceById(identity.getTeacherId())); roster.saveAndFlush(entity);
        return catalogs.listLecturers(school).stream().filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
    }
    AccountBatch batch(LecturerView... rows) { return new AccountBatch(Arrays.stream(rows).map(r -> new AccountTarget(r.id(), r.revision())).toList()); }

    @Test void creatingAnActiveLecturerCreatesHashedCredentialsAndCanLogInByLecturerCode() {
        long count = teachers.count(); var row = catalogs.createLecturer(school, input(code(), "Đặng Thị Mỹ Lệ", null, true, null));
        var account = teachers.findById(row.accountTeacherId()).orElseThrow();
        assertEquals(count + 1, teachers.count()); assertEquals(code(), account.getUsername()); assertEquals("TEACHER", account.getRole());
        assertTrue(row.autoProvisioned()); assertTrue(encoder.matches("dangthimyle" + code(), account.getPassword())); assertNotEquals("dangthimyle" + code(), account.getPassword());
        var login = new AuthRequest(); login.setUsername(code()); login.setPassword("dangthimyle" + code());
        var result = auth.login(login); assertEquals(row.accountTeacherId(), result.getTeacherId()); assertNotNull(result.getAccessToken());
        assertTrue(account.getSchools().isEmpty()); assertNull(row.scheduleTeacherId());
    }

    @Test void nameEditsAndRepeatedImportsDoNotResetAnExistingPasswordOrCreateMoreAccounts() throws Exception {
        var row = catalogs.createLecturer(school, input(code(), "Đặng Thị Mỹ Lệ", null, true, null)); var account = teachers.findById(row.accountTeacherId()).orElseThrow();
        auth.changePassword(new AccountPrincipal(account.getTeacherId(), account.getUsername(), "TEACHER"), new ChangePasswordRequest("dangthimyle" + code(), "ChangedPassword8"));
        String hash = account.getPassword(); long count = teachers.count();
        var changed = catalogs.updateLecturer(school, row.id(), input(code(), "Tên mới của GV", null, true, row.revision()));
        assertEquals(row.accountTeacherId(), changed.accountTeacherId()); assertEquals(hash, account.getPassword());
        var file = CatalogImportIntegrationTest.file(new String[]{"Mã GV", "Họ tên"}, new Object[][]{{code(), "Tên mới của GV"}});
        var preview = imports.preview(school, Kind.LECTURERS, file); assertEquals(1, preview.unchanged());
        imports.commit(school, Kind.LECTURERS, file, preview.snapshot()); assertEquals(count, teachers.count()); assertEquals(hash, account.getPassword());
    }

    @Test void adminAndOtherPeoplesLoginsAreNeverOverwrittenOrGuessedAcrossSchools() {
        String adminHash = teachers.findById(adminId).orElseThrow().getPassword();
        assertThrows(IllegalArgumentException.class, () -> catalogs.createLecturer(school, input(adminId.toLowerCase(Locale.ROOT), "Một GV khác", null, true, null)));
        assertEquals(adminHash, teachers.findById(adminId).orElseThrow().getPassword());
        var first = catalogs.createLecturer(school, input(code(), "Đặng Thị Mỹ Lệ", null, true, null));
        String other = school + "_B"; catalogs.createInstitution(new InstitutionInput(other, "Trường B", true, null));
        assertThrows(IllegalArgumentException.class, () -> catalogs.createLecturer(other, new LecturerInput(code(), "Người khác", null, LecturerType.STAFF, null, true, null)));
        var linked = catalogs.createLecturer(other, new LecturerInput(code(), "Đặng Thị Mỹ Lệ", null, LecturerType.STAFF, first.accountTeacherId(), true, null));
        assertEquals(first.accountTeacherId(), linked.accountTeacherId());
    }

    @Test void promotingAnExplicitLegacyIdentityKeepsItsCalendarAndInternalIdentifier() {
        var teacher = Teacher.builder().teacherId("OLD_" + school).fullName("Đặng Thị Mỹ Lệ").semesterStartDate(CatalogSchedulingTest.START).build();
        var oldSchool = School.builder().teacher(teacher).schoolCode(school).schoolName("Trường").build(); teacher.getSchools().add(oldSchool);
        oldSchool.getCourses().add(TeachingCourse.builder().school(oldSchool).courseCode("NET").courseName("Lịch cũ").classroom("101").dayOfWeek(2).startPeriod(1).numPeriods(1).weekSchedule("1").build());
        var saved = teachers.saveAndFlush(teacher); var savedSchool = saved.getSchools().getFirst();
        Long oldCourseId = savedSchool.getCourses().iterator().next().getId(); assertNotNull(oldCourseId); var row = legacy(code(), saved); long count = teachers.count();
        var result = catalogs.createMissingAccounts(school, batch(row)); assertEquals(1, result.created()); var promoted = result.lecturers().getFirst();
        assertEquals(teacher.getTeacherId(), promoted.accountTeacherId()); assertEquals(teacher.getTeacherId(), promoted.scheduleTeacherId()); assertEquals(count, teachers.count());
        assertEquals(code(), saved.getUsername()); assertTrue(encoder.matches("dangthimyle" + code(), saved.getPassword())); assertEquals(oldCourseId, savedSchool.getCourses().iterator().next().getId());
        assertEquals(1, saved.getSchools().size());
        assertEquals(0, catalogs.createMissingAccounts(school, batch(promoted)).created());
    }

    @Test @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void aLateBatchCollisionRollsBackEveryEarlierAccountAndRosterLink() {
        var a = legacy(code(), null); var b = legacy(adminId, null); long count = teachers.count();
        assertThrows(IllegalArgumentException.class, () -> catalogs.createMissingAccounts(school, batch(a, b)));
        entityManager.clear(); assertEquals(count, teachers.count()); assertNull(roster.findById(a.id()).orElseThrow().getAccount()); assertFalse(teachers.existsById(code()));
    }

    @Test void importPreviewIsReadOnlyAndCommitCreatesTheAccountOnlyOnce() throws Exception {
        var file = CatalogImportIntegrationTest.file(new String[]{"Mã GV", "Họ tên"}, new Object[][]{{code(), "Đặng Thị Mỹ Lệ"}}); long count = teachers.count();
        var preview = imports.preview(school, Kind.LECTURERS, file); assertEquals(0, preview.invalid()); assertEquals(count, teachers.count());
        imports.commit(school, Kind.LECTURERS, file, preview.snapshot()); assertEquals(count + 1, teachers.count()); assertEquals(1, imports.preview(school, Kind.LECTURERS, file).unchanged());
        assertTrue(encoder.matches("dangthimyle" + code(), teachers.findByUsername(code()).orElseThrow().getPassword()));
    }

    @Test void aLoginCollisionAppearingAfterPreviewRequiresReviewAndWritesNothing() throws Exception {
        var file = CatalogImportIntegrationTest.file(new String[]{"Mã GV", "Họ tên"}, new Object[][]{{code(), "Đặng Thị Mỹ Lệ"}});
        var preview = imports.preview(school, Kind.LECTURERS, file);
        teachers.saveAndFlush(Teacher.builder().teacherId(school + "_OTHER").username(code().toLowerCase(Locale.ROOT)).semesterStartDate(CatalogSchedulingTest.START).build());
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> imports.commit(school, Kind.LECTURERS, file, preview.snapshot()));
        assertFalse(teachers.existsById(code())); assertTrue(catalogs.listLecturers(school).stream().noneMatch(r -> r.lecturerCode().equals(code())));
    }

    @Test void bulkEndpointRequiresAdminAndNeverReturnsPasswordOrHash() throws Exception {
        var row = legacy(code(), null); String path = "/api/admin/institutions/" + school + "/lecturer-accounts";
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(post(path).header("Authorization", "Bearer " + tokens.issue(userId)).contentType("application/json").content(json.writeValueAsString(batch(row)))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + tokens.issue(adminId)).contentType("application/json").content(json.writeValueAsString(batch(row))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.lecturers[0].accountUsername").value(code()))
                .andExpect(jsonPath("$.lecturers[0].password").doesNotExist()).andExpect(jsonPath("$.lecturers[0].passwordHash").doesNotExist());
    }

    @Test void legacySchedulingCreatesCredentialsOnlyAfterACompleteSuccessfulSave() {
        var request = WeeklyScheduleEngineTest.request(3, 0); request.getTargetSchool().setSchoolId(school); request.getTargetSchool().setSchoolName("Trường");
        request.getCourses().getFirst().setTeachers(List.of(TeacherCandidateDTO.builder().teacherId(code()).teacherName("Đặng Thị Mỹ Lệ").theoryWeight(1).practiceWeight(0).build()));
        var small = Room.builder().schoolCode(school).roomCode("SMALL").capacity(1).isLab(false).build();
        assertFalse(automatic.generateScheduleWithRooms(request, List.of(small)).isSaved()); assertFalse(teachers.existsById(code()));
        var good = Room.builder().schoolCode(school).roomCode("101").capacity(60).isLab(false).build();
        assertTrue(automatic.generateScheduleWithRooms(request, List.of(good)).isSaved()); assertTrue(encoder.matches("dangthimyle" + code(), teachers.findById(code()).orElseThrow().getPassword()));
    }

    @Test void oldUnchangedRosterRowsAreReviewedAsAccountUpdatesAndProvisionedOnImport() throws Exception {
        var old = legacy(code(), null);
        var file = CatalogImportIntegrationTest.file(new String[]{"Mã GV", "Họ tên"}, new Object[][]{{code(), old.fullName()}});
        var preview = imports.preview(school, Kind.LECTURERS, file); assertEquals(1, preview.updated()); assertTrue(preview.canCommit());
        assertEquals(code(), preview.rows().getFirst().values().get("accountUsername")); assertFalse(teachers.existsById(code()));
        imports.commit(school, Kind.LECTURERS, file, preview.snapshot()); assertTrue(teachers.existsById(code()));
    }

    @Test void confirmingAnOldUnlinkedDraftProvisionsItsAccountAndRemainsEditableWithoutIdentityConflicts() throws Exception {
        var old = legacy(code(), null); catalogs.createAssignment(school, old.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        var p = generate(create(department), old, 6); long before = teachers.count();
        assertNull(p.draft().result().getCatalogContext().lecturers().getFirst().accountTeacherId()); assertFalse(teachers.existsById(code()));
        p = plans.confirm(p.plan().id(), p.plan().revision()); assertEquals(before + 1, teachers.count());
        assertTrue(plans.validate(p.plan().id(), p.plan().revision()).isEmpty());
        var source = adjustments.options(p.plan().id()); var first = plans.entryPage(p.plan().id(), p.draft().summary().id(), 0).rows().getFirst();
        var input = new com.dto.ScheduleAdjustmentDTO.Input(p.plan().revision(), p.draft().summary().id(), source.snapshot(), "LOCK", List.of(first.id()), List.of(), List.of(), null, true, null, null, null, null, false, "Khóa sau tạo tài khoản");
        assertTrue(adjustments.preview(p.plan().id(), input).canConfirm());
    }

    @Test void lowerCaseLegacyInputResolvesTheCanonicalAccountAndDoesNotCreateDuplicates() {
        var row = catalogs.createLecturer(school, input(code(), "Đặng Thị Mỹ Lệ", null, true, null)); long count = teachers.count();
        var request = WeeklyScheduleEngineTest.request(3, 0); request.getTargetSchool().setSchoolId(school); request.getTargetSchool().setSchoolName("Trường");
        request.getCourses().getFirst().setTeachers(List.of(TeacherCandidateDTO.builder().teacherId(code().toLowerCase(Locale.ROOT)).teacherName("Đặng Thị Mỹ Lệ").theoryWeight(1).practiceWeight(0).build()));
        var room = Room.builder().schoolCode(school).roomCode("101").capacity(60).isLab(false).build();
        var result = automatic.generateScheduleWithRooms(request, List.of(room)); assertTrue(result.isSaved());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getTeacherId().equals(row.accountTeacherId())));
        assertEquals(count, teachers.count());
    }
}
