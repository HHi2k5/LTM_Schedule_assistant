package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.*;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import com.security.AccountPrincipal;
import com.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CatalogSchedulingTest {
    @Autowired CatalogService catalogs;
    @Autowired CatalogSchedulingService scheduler;
    @Autowired TeacherRepository teachers;
    @Autowired ScheduleService schedules;
    @Autowired InstitutionLecturerRepository lecturers;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static final LocalDate START = LocalDate.of(2026, 8, 10);
    LecturerView lecturer;
    AssignmentView assignment;
    RoomView room;

    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal("CS_ADMIN", "manager", "ADMIN"), null, List.of()));
        teachers.saveAndFlush(Teacher.builder().teacherId("CS_USER").username("catalog-schedule-user").password("unchanged-hash").fullName("Account Name").role("TEACHER").semesterStartDate(START).build());
        teachers.saveAndFlush(Teacher.builder().teacherId("CS_ADMIN").username("catalog-schedule-admin").role("ADMIN").semesterStartDate(START).build());
        catalogs.createInstitution(new InstitutionInput("CS_A", "Trường A", true, null));
        catalogs.createInstitution(new InstitutionInput("CS_B", "Trường B", true, null));
        lecturer = catalogs.createLecturer("CS_A", new LecturerInput("STAFF01", "Tên danh mục", null, LecturerType.STAFF, "CS_USER", true, null));
        assignment = catalogs.createAssignment("CS_A", lecturer.id(), new AssignmentInput("NET", "Networking", "2024", 2, 8, true, null));
        room = catalogs.createRoom("CS_A", new RoomInput("101", "Phòng 101", "THEORY", 60, "A2", true, null));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void onlyPlanFileIsNeededAndLinkedAccountOwnsTheSavedLessons() throws Exception {
        var options = scheduler.options("CS_A"); long accountCount = teachers.count();
        assertTrue(options.lecturers().getFirst().eligible()); assertEquals(12, options.periodChoices().getFirst().periods().size());
        var result = scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(lecturer.id()), List.of(room.id()), List.of()));
        assertTrue(result.isSaved(), result.getUnscheduledWarnings().toString());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> "CS_USER".equals(s.getTeacherId())));
        assertEquals("STAFF01", result.getCatalogContext().lecturers().getFirst().lecturerCode());
        assertEquals("CS_USER", catalogs.listLecturers("CS_A").getFirst().scheduleTeacherId());
        int firstWeek = result.getScheduledClasses().getFirst().getWeekSchedule().chars().takeWhile(c -> c == '-' || c == ' ').toArray().length + 1;
        assertFalse(schedules.getWeeklySchedule("CS_USER", firstWeek).getLessons().isEmpty());
        assertEquals(accountCount, teachers.count()); assertEquals("unchanged-hash", teachers.findById("CS_USER").orElseThrow().getPassword());
        assertEquals(2, catalogs.listAssignments("CS_A", lecturer.id()).getFirst().theoryWeight());
    }

    @Test void automaticLecturerAccountHasStableIdentityAndCannotClaimAClashingAdminCode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> catalogs.createLecturer("CS_A", new LecturerInput("CS_ADMIN", "Giảng viên khác", null, LecturerType.VISITING, null, true, null)));
        var unlinked = catalogs.createLecturer("CS_A", new LecturerInput("NEW_AUTO", "Giảng viên khác", null, LecturerType.VISITING, null, true, null));
        catalogs.createAssignment("CS_A", unlinked.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        var options = scheduler.options("CS_A");
        var result = scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(unlinked.id()), List.of(room.id()), List.of()));
        assertTrue(result.isSaved());
        String id = result.getCatalogContext().lecturers().getFirst().teacherId();
        assertEquals("NEW_AUTO", id);
        var profile = teachers.findById(id).orElseThrow(); assertEquals("NEW_AUTO", profile.getUsername()); assertNotNull(profile.getPassword()); assertTrue(profile.isAutoProvisioned());
        options = scheduler.options("CS_A");
        var repeated = scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(unlinked.id()), List.of(room.id()), List.of()));
        assertEquals(id, repeated.getCatalogContext().lecturers().getFirst().teacherId());
        assertEquals("ADMIN", teachers.findById("CS_ADMIN").orElseThrow().getRole());
    }

    @Test void runWeightsOverrideAllocationWithoutChangingDefaultsAndUseOnlySelectedRooms() throws Exception {
        var options = scheduler.options("CS_A");
        var result = scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(lecturer.id()), List.of(room.id()),
                List.of(new WeightOverride(assignment.id(), 9, 0))));
        assertTrue(result.isSaved()); assertEquals(9, result.getTeacherAllocations().stream().filter(a -> a.getSessionType().equals("THEORY")).findFirst().orElseThrow().getWeight());
        assertEquals(2, catalogs.listAssignments("CS_A", lecturer.id()).getFirst().theoryWeight());
        assertEquals(8, catalogs.listAssignments("CS_A", lecturer.id()).getFirst().practiceWeight());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getRoom().equals("101")));
    }

    @Test void changingCatalogAfterLoadingOptionsRejectsRunWithoutSavingSchedules() throws Exception {
        var options = scheduler.options("CS_A"); var configuration = config(options, List.of(lecturer.id()), List.of(room.id()), List.of());
        catalogs.updateRoom("CS_A", room.id(), new RoomInput("101", "Tên mới", "THEORY", 70, "A2", true, room.revision()));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> scheduler.generate("CS_A", START, plan(6, 0), configuration));
        assertTrue(teachers.findById("CS_USER").orElseThrow().getSchools().isEmpty());
    }

    @Test void rejectsForeignRoomAndInactiveLecturerAndForeignWeights() throws Exception {
        var foreign = catalogs.createRoom("CS_B", new RoomInput("101", "B", "THEORY", 60, null, true, null));
        var options = scheduler.options("CS_A");
        assertThrows(IllegalArgumentException.class, () -> scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(lecturer.id()), List.of(foreign.id()), List.of())));
        assertThrows(IllegalArgumentException.class, () -> scheduler.generate("CS_A", START, plan(6, 0), config(options, List.of(lecturer.id()), List.of(room.id()), List.of(new WeightOverride(999999L, 1, 1)))));
    }

    @Test void missingCourseCohortAndZeroTheoryWeightReportTheSpecificDemand() throws Exception {
        var options = scheduler.options("CS_A");
        var error = assertThrows(IllegalArgumentException.class, () -> scheduler.generate("CS_A", START, plan(6, 0),
                config(options, List.of(lecturer.id()), List.of(room.id()), List.of(new WeightOverride(assignment.id(), 0, 8)))));
        assertTrue(error.getMessage().contains("NET/2024")); assertTrue(error.getMessage().contains("LT"));
        assertNull(catalogs.listLecturers("CS_A").getFirst().scheduleTeacherId());
    }

    @Test void anInactiveFacultyDisablesItsLecturersAndAnInactiveRoomIsNotOffered() throws Exception {
        var department = catalogs.createDepartment("CS_A", new DepartmentInput("FIT", "Khoa", false, null));
        catalogs.updateLecturer("CS_A", lecturer.id(), new LecturerInput("STAFF01", "GV", department.id(), LecturerType.STAFF, "CS_USER", true, lecturer.revision()));
        catalogs.updateRoom("CS_A", room.id(), new RoomInput("101", "Phòng 101", "THEORY", 60, "A2", false, room.revision()));
        var options = scheduler.options("CS_A"); assertFalse(options.lecturers().getFirst().eligible()); assertTrue(options.rooms().isEmpty());
    }

    @Test void explicitLegacyIdentityRetainsOldBusyTimesAndCannotBeRelinkedSilentlyAfterScheduling() throws Exception {
        var legacy = Teacher.builder().teacherId("CS_LEGACY").fullName("GV cũ").semesterStartDate(START).build();
        var oldSchool = BackendRegressionTest.school("CS_A", "1"); oldSchool.setTeacher(legacy);
        oldSchool.getCourses().forEach(c -> c.setSchool(oldSchool)); oldSchool.getPeriods().forEach(p -> p.setSchool(oldSchool));
        legacy.getSchools().add(oldSchool); teachers.saveAndFlush(legacy);
        var profile = catalogs.createLecturer("CS_A", new LecturerInput("LEGACY_STAFF", "GV cũ", null, LecturerType.STAFF, null, true, null, "CS_LEGACY"));
        assertEquals("CS_LEGACY", profile.scheduleTeacherId());
        assertThrows(IllegalArgumentException.class, () -> catalogs.updateLecturer("CS_A", profile.id(), new LecturerInput("LEGACY_STAFF", "GV cũ", null, LecturerType.STAFF, "CS_USER", true, profile.revision())));
    }

    @Test void oldPeriodChoicesRequireExplicitSelectionAndAreNotReplacedByDefaultTwelvePeriods() throws Exception {
        var input = BackendRegressionTest.upload(); input.setTeacherId("CS_USER"); input.getSchool().setSchoolId("CS_A");
        input.getSchool().setPeriods(List.of(new PeriodTimeDTO(1, java.time.LocalTime.of(7, 20), java.time.LocalTime.of(8, 10))));
        schedules.saveOrUpdateSchoolSchedule(input);
        var options = scheduler.options("CS_A");
        assertEquals(1, options.periodChoices().size()); assertEquals(1, options.periodChoices().getFirst().periods().size());
        assertEquals(java.time.LocalTime.of(7, 20), options.periodChoices().getFirst().periods().getFirst().getStart());
    }

    @Test void schedulingApiIsAdminOnlyAndOptionsDoNotExposeCredentials() throws Exception {
        SecurityContextHolder.clearContext();
        String path = "/api/admin/institutions/CS_A/scheduling-options";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + tokens.issue("CS_USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", "Bearer " + tokens.issue("CS_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturers[0].lecturer.accountTeacherId").value("CS_USER"))
                .andExpect(jsonPath("$.lecturers[0].lecturer.password").doesNotExist());
        var configuration = config(scheduler.options("CS_A"), List.of(lecturer.id()), List.of(room.id()), List.of());
        var file = new org.springframework.mock.web.MockMultipartFile("planFile", "plan.xlsx", "application/octet-stream", plan(6, 0).getBytes());
        mvc.perform(multipart("/api/schedule/auto-generate-catalog").file(file).param("schoolId", "CS_A").param("semesterStartDate", START.toString())
                .param("configuration", json.writeValueAsString(configuration)).header("Authorization", "Bearer " + tokens.issue("CS_USER"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/schedule/auto-generate-catalog").param("schoolId", "CS_A").param("semesterStartDate", START.toString())
                .param("configuration", json.writeValueAsString(configuration)).header("Authorization", "Bearer " + tokens.issue("CS_ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/schedule/auto-generate-catalog").file(file).param("schoolId", "CS_A").param("semesterStartDate", START.toString())
                .param("configuration", json.writeValueAsString(configuration)).header("Authorization", "Bearer " + tokens.issue("CS_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.catalogContext.lecturers[0].teacherId").value("CS_USER"));
    }

    static org.springframework.mock.web.MockMultipartFile plan(int theory, int practice) throws Exception {
        var row = WeeklyPlanExcelReaderTest.row(); row[9] = theory; row[10] = practice;
        return WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.HEADERS, row});
    }
    static Configuration config(Options options, List<Long> lecturers, List<Long> rooms, List<WeightOverride> weights) {
        return new Configuration(options.snapshot(), lecturers, rooms, weights, options.periodChoices().getFirst().id(), true);
    }
}
