package com.service;

import com.dto.CatalogDTO.*;
import com.model.InstitutionLecturer.LecturerType;
import com.model.Teacher;
import com.repository.*;
import com.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrationTest {
    private static final String ROOT = "/api/admin/institutions";
    @Autowired CatalogService catalogs;
    @Autowired TeacherRepository accounts;
    @Autowired RoomRepository rooms;
    @Autowired TokenService tokens;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String adminToken;
    String teacherToken;

    @BeforeEach void setup() {
        account("CAT_ADMIN", "ADMIN"); account("CAT_TEACHER", "TEACHER");
        adminToken = "Bearer " + tokens.issue("CAT_ADMIN");
        teacherToken = "Bearer " + tokens.issue("CAT_TEACHER");
        catalogs.createInstitution(new InstitutionInput(" cat_a ", "Trường A", null, null));
        catalogs.createInstitution(new InstitutionInput("CAT_B", "Trường B", true, null));
    }

    @Test void directorySurvivesReloadWithAnAutomaticAccountAndPreservesExistingAccountsAndSchedules() {
        long count = accounts.count();
        String password = accounts.findById("CAT_TEACHER").orElseThrow().getPassword();
        var department = catalogs.createDepartment("CAT_A", new DepartmentInput(" fit ", "Công nghệ thông tin", null, null));
        var lecturer = catalogs.createLecturer("CAT_A", lecturer(" gv01 ", department.id(), null));
        catalogs.createAssignment("CAT_A", lecturer.id(), assignment(" csdl ", 2, 8));
        catalogs.createRoom("CAT_A", room(" 101-a2 ", null));
        entityManager.flush(); entityManager.clear();

        assertEquals("FIT", catalogs.listDepartments("cat_a").getFirst().departmentCode());
        var stored = catalogs.listLecturers("CAT_A").getFirst();
        assertEquals("GV01", stored.lecturerCode()); assertEquals("GV01", stored.accountUsername()); assertTrue(stored.autoProvisioned());
        assertEquals(department.id(), stored.departmentId()); assertTrue(stored.active());
        var assignment = catalogs.listAssignments("CAT_A", lecturer.id()).getFirst();
        assertEquals("CSDL", assignment.courseCode()); assertEquals(2, assignment.theoryWeight()); assertEquals(8, assignment.practiceWeight());
        assertEquals("101-A2", catalogs.listRooms("CAT_A").getFirst().roomCode());
        assertEquals(count + 1, accounts.count());
        var account = accounts.findById("CAT_TEACHER").orElseThrow();
        assertEquals(password, account.getPassword()); assertEquals("TEACHER", account.getRole());
        assertTrue(account.getSchools().isEmpty());
    }

    @Test void accountLinkIsExplicitAndCredentialFieldsNeverAppearInApiResponse() throws Exception {
        var input = lecturer("STAFF01", null, "CAT_TEACHER");
        mvc.perform(post(ROOT + "/CAT_A/lecturers").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountTeacherId").value("CAT_TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.account").doesNotExist()).andExpect(jsonPath("$.role").doesNotExist());
        mvc.perform(get(ROOT + "/CAT_A/lecturers").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].lecturerCode").value("STAFF01"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test void lecturerCannotBelongToAnotherSchoolsDepartment() throws Exception {
        var foreign = catalogs.createDepartment("CAT_B", new DepartmentInput("FIT", "Khoa B", true, null));
        mvc.perform(post(ROOT + "/CAT_A/lecturers").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(lecturer("GV01", foreign.id(), null))))
                .andExpect(status().isNotFound());
    }

    @Test void lecturerAndRoomIdsCannotBeUsedThroughAnotherSchoolsUrl() throws Exception {
        var lecturer = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        var room = catalogs.createRoom("CAT_A", room("101", true));
        mvc.perform(get(ROOT + "/CAT_B/lecturers/" + lecturer.id() + "/assignments").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(put(ROOT + "/CAT_B/rooms/" + room.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                new RoomInput("101", "Phòng 101", "THEORY", 60, "A2", true, room.revision()))))
                .andExpect(status().isNotFound());
    }

    @Test void assignmentIdCannotBeMovedBetweenLecturers() throws Exception {
        var a = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        var b = catalogs.createLecturer("CAT_A", lecturer("GV02", null, null));
        var assignment = catalogs.createAssignment("CAT_A", a.id(), assignment("NET", 1, 1));
        mvc.perform(put(ROOT + "/CAT_A/lecturers/" + b.id() + "/assignments/" + assignment.id())
                        .header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new AssignmentInput("NET", "Mạng", "2024", 2, 8, true, assignment.revision()))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"CAT_ADMIN", "UNKNOWN"})
    void accountLinkRejectsAdminOrMissingAccount(String accountId) throws Exception {
        mvc.perform(post(ROOT + "/CAT_A/lecturers").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(lecturer("GV01", null, accountId))))
                .andExpect(status().is(accountId.equals("UNKNOWN") ? 404 : 400));
    }

    @Test void sameDirectoryCodesAreAllowedAcrossSchools() {
        var a = catalogs.createLecturer("CAT_A", lecturer("GV01", null, "CAT_TEACHER"));
        var b = catalogs.createLecturer("CAT_B", lecturer("GV01", null, "CAT_TEACHER"));
        catalogs.createRoom("CAT_A", room("101", true)); catalogs.createRoom("CAT_B", room("101", true));
        assertNotEquals(a.id(), b.id());
        assertEquals(1, catalogs.listRooms("CAT_A").size()); assertEquals(1, catalogs.listRooms("CAT_B").size());
    }

    @ParameterizedTest @ValueSource(strings = {"institution", "department", "lecturer", "assignment", "room", "account"})
    void databaseRejectsDuplicateBusinessKeys(String kind) {
        switch (kind) {
            case "institution" -> assertThrows(DataIntegrityViolationException.class,
                    () -> catalogs.createInstitution(new InstitutionInput("cat_a", "Duplicate", true, null)));
            case "department" -> {
                catalogs.createDepartment("CAT_A", new DepartmentInput("FIT", "Khoa", true, null));
                assertThrows(DataIntegrityViolationException.class, () -> catalogs.createDepartment("CAT_A", new DepartmentInput(" fit ", "Khoa", true, null)));
            }
            case "lecturer", "account" -> {
                catalogs.createLecturer("CAT_A", lecturer("GV01", null, "CAT_TEACHER"));
                assertThrows(DataIntegrityViolationException.class,
                        () -> catalogs.createLecturer("CAT_A", lecturer(kind.equals("account") ? "GV02" : " gv01 ", null, kind.equals("account") ? "CAT_TEACHER" : null)));
            }
            case "assignment" -> {
                var lecturer = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
                catalogs.createAssignment("CAT_A", lecturer.id(), assignment("NET", 1, 1));
                assertThrows(DataIntegrityViolationException.class, () -> catalogs.createAssignment("CAT_A", lecturer.id(), assignment(" net ", 2, 8)));
            }
            case "room" -> {
                catalogs.createRoom("CAT_A", room("101", true));
                assertThrows(DataIntegrityViolationException.class, () -> catalogs.createRoom("CAT_A", room("101", true)));
            }
            default -> fail("Unknown fixture");
        }
    }

    @Test void deactivationPreservesAssignmentAndRejectsStaleEdits() throws Exception {
        var lecturer = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        var assignment = catalogs.createAssignment("CAT_A", lecturer.id(), assignment("NET", 1, 8));
        var updated = catalogs.updateLecturer("CAT_A", lecturer.id(),
                new LecturerInput("GV01", "Tên đã sửa", null, LecturerType.VISITING, null, false, lecturer.revision()));
        assertFalse(updated.active()); assertTrue(updated.revision() > lecturer.revision());
        entityManager.clear();
        assertEquals(assignment.id(), catalogs.listAssignments("CAT_A", lecturer.id()).getFirst().id());
        mvc.perform(put(ROOT + "/CAT_A/lecturers/" + lecturer.id()).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                new LecturerInput("GV01", "Cập nhật cũ", null, LecturerType.STAFF, null, true, lecturer.revision()))))
                .andExpect(status().isConflict());
    }

    @Test void updatedAssignmentDoesNotModifyAnotherLecturersDefaultWeights() {
        var a = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        var b = catalogs.createLecturer("CAT_A", lecturer("GV02", null, null));
        var assignment = catalogs.createAssignment("CAT_A", a.id(), assignment("NET", 1, 8));
        catalogs.createAssignment("CAT_A", b.id(), assignment("NET", 9, 2));
        catalogs.updateAssignment("CAT_A", a.id(), assignment.id(),
                new AssignmentInput("NET", "Tên mới", "2024", 3, 7, true, assignment.revision()));
        entityManager.clear();
        assertEquals(3, catalogs.listAssignments("CAT_A", a.id()).getFirst().theoryWeight());
        assertEquals(9, catalogs.listAssignments("CAT_A", b.id()).getFirst().theoryWeight());
    }

    @ParameterizedTest @ValueSource(strings = {"missingRevision", "missingActive", "rename"})
    void updatesRequireRevisionAndExplicitStatusAndKeepIdentifiersStable(String kind) throws Exception {
        mvc.perform(put(ROOT + "/CAT_A").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new InstitutionInput(kind.equals("rename") ? "NEW_CODE" : "CAT_A", "Tên mới",
                                kind.equals("missingActive") ? null : true, kind.equals("missingRevision") ? null : 0L))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 10001})
    void invalidWeightsAreRejected(int weight) throws Exception {
        var lecturer = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        mvc.perform(post(ROOT + "/CAT_A/lecturers/" + lecturer.id() + "/assignments").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(assignment("NET", weight, 1))))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidRoomAndEmptyActiveAssignmentAreRejected() {
        var lecturer = catalogs.createLecturer("CAT_A", lecturer("GV01", null, null));
        assertThrows(IllegalArgumentException.class, () -> catalogs.createAssignment("CAT_A", lecturer.id(), assignment("NET", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> catalogs.createRoom("CAT_A", new RoomInput("101", "Phòng", "UNKNOWN", 60, null, true, null)));
    }

    @Test void inactiveRoomRemainsInDirectoryButDisappearsFromSchedulingPool() throws Exception {
        var room = catalogs.createRoom("CAT_A", room("101", true));
        catalogs.updateRoom("CAT_A", room.id(), new RoomInput("101", "Phòng 101", "THEORY", 60, "A2", false, room.revision()));
        entityManager.clear();
        assertEquals(1, catalogs.listRooms("CAT_A").size()); assertFalse(catalogs.listRooms("CAT_A").getFirst().active());
        mvc.perform(get("/api/schedule/rooms").param("schoolId", "CAT_A").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void anonymousAndTeacherCannotReadOrChangeCatalogs() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        for (String url : List.of(ROOT, ROOT + "/CAT_A/departments", ROOT + "/CAT_A/lecturers", ROOT + "/CAT_A/rooms")) {
            mvc.perform(get(url).header("Authorization", teacherToken)).andExpect(status().isForbidden());
            mvc.perform(post(url).header("Authorization", teacherToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(ROOT + "/CAT_A").header("Authorization", teacherToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void institutionAndDepartmentUpdatePersistWithNewRevisions() {
        var department = catalogs.createDepartment("CAT_A", new DepartmentInput("FIT", "Khoa", true, null));
        var result = catalogs.updateDepartment("CAT_A", department.id(), new DepartmentInput("FIT", "Tên khoa mới", false, department.revision()));
        var school = catalogs.updateInstitution("CAT_A", new InstitutionInput("CAT_A", "Tên trường mới", false, 0L));
        entityManager.clear();
        assertFalse(result.active()); assertTrue(result.revision() > department.revision());
        assertFalse(school.active()); assertEquals("Tên trường mới", catalogs.listInstitutions().stream().filter(s -> s.schoolCode().equals("CAT_A")).findFirst().orElseThrow().name());
    }

    private void account(String id, String role) {
        accounts.saveAndFlush(Teacher.builder().teacherId(id).username(id).fullName(id).role(role)
                .password("credential-must-not-be-exposed").semesterStartDate(LocalDate.of(2026, 8, 10)).build());
    }
    private LecturerInput lecturer(String code, Long department, String account) { return new LecturerInput(code, "Nguyễn Văn A", department, LecturerType.STAFF, account, null, null); }
    private AssignmentInput assignment(String code, int theory, int practice) { return new AssignmentInput(code, "Môn học", "2024", theory, practice, null, null); }
    private RoomInput room(String code, Boolean active) { return new RoomInput(code, "Phòng " + code, "THEORY", 60, "A2", active, null); }
}
