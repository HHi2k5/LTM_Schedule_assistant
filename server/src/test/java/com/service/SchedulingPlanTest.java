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
