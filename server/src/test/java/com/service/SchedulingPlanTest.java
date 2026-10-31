package com.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SchedulingPlanTest extends SchedulingPlanContract {
    @Test void bev1PlanRowsCanUseSavedCatalogsAndPublishCompletePersonalSchedules() throws Exception {
        var assignment = catalogs.listAssignments(school, lecturer.id()).getFirst();
        catalogs.updateAssignment(school, lecturer.id(), assignment.id(), new com.dto.CatalogDTO.AssignmentInput("NET", "Networking", "2024", 1, 1, true, assignment.revision()));
        var lab = catalogs.createRoom(school, new com.dto.CatalogDTO.RoomInput("LAB01", "Phòng máy", "PRACTICE", 30, "B", true, null));
        var p = create(department); var source = plans.options(p.plan().id());
        var lt = WeeklyPlanExcelReaderTest.typedRow("LT"); lt[10] = 6;
        var th = WeeklyPlanExcelReaderTest.typedRow("TH"); th[4] = 25; th[6] = 2; th[10] = 3;
        var configuration = new com.dto.CatalogSchedulingDTO.Configuration(source.snapshot(), java.util.List.of(lecturer.id()), java.util.List.of(room.id(), lab.id()), java.util.List.of(), source.periodChoices().getFirst().id(), false);
        p = plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.TYPED_HEADERS, lt, th}), configuration);
        assertTrue(p.draft().summary().complete()); assertEquals(2, p.draft().courses().size()); assertEquals(3, p.draft().result().getClassWorkloads().size());
        assertTrue(projection(p.plan().id()).isEmpty());
        p = plans.confirm(p.plan().id(), p.plan().revision());
        assertEquals("CONFIRMED", p.plan().state());
        assertEquals(12, projection(p.plan().id()).stream().mapToInt(com.model.TeachingCourse::getNumPeriods).sum());
        assertFalse(calendars.getWeeklySchedule(lecturer.accountTeacherId(), 1).getLessons().isEmpty());
    }
    @Test void multipartDraftEndpointAndJsonConfirmationPublishTheSameStoredVersion() throws Exception {
        SecurityContextHolder.clearContext(); String auth = "Bearer " + tokens.issue(adminId); String root = "/api/admin/scheduling-plans";
        var created = mvc.perform(post(root).header("Authorization", auth).contentType("application/json").content(json.writeValueAsString(Map.of(
                "schoolCode", school, "departmentId", department.id(), "name", "Bản lịch API", "semester", "HK1-API", "semesterStartDate", CatalogSchedulingTest.START.toString()))))
                .andExpect(status().isCreated()).andReturn();
        var initial = json.readTree(created.getResponse().getContentAsString()); long id = initial.path("plan").path("id").asLong();
        var source = mvc.perform(get(root + "/" + id + "/options").header("Authorization", auth)).andExpect(status().isOk()).andReturn();
        var options = json.readValue(source.getResponse().getContentAsByteArray(), com.dto.CatalogSchedulingDTO.Options.class);
        var file = new MockMultipartFile("planFile", "plan.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", CatalogSchedulingTest.plan(6, 0).getBytes());
        var response = mvc.perform(multipart(root + "/" + id + "/generate-preview").file(file).param("revision", "0")
                .param("configuration", json.writeValueAsString(config(options, lecturer.id(), room.id()))).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft.result.saved").value(false)).andExpect(jsonPath("$.draft.summary.complete").value(true)).andReturn();
        var draft = json.readTree(response.getResponse().getContentAsString()); long revision = draft.path("plan").path("revision").asLong();
        var published = mvc.perform(post(root + "/" + id + "/confirm").header("Authorization", auth).contentType("application/json").content("{\"revision\":" + revision + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft.result.saved").value(true)).andExpect(jsonPath("$.plan.state").value("CONFIRMED")).andReturn();
        assertEquals(draft.path("draft").path("summary").path("id"), json.readTree(published.getResponse().getContentAsString()).path("draft").path("summary").path("id"));
    }
}
