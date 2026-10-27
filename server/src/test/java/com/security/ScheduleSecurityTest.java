package com.security;

import com.config.SecurityConfig;
import com.controller.ScheduleController;
import com.dto.AutoScheduleResponse;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.repository.RoomRepository;
import com.service.*;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, TokenFilter.class, ScheduleAccess.class})
class ScheduleSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean TokenService tokens;
    @MockBean TeacherRepository teachers;
    @MockBean RoomRepository rooms;
    @MockBean ScheduleService schedules;
    @MockBean AutoScheduleService automatic;
    @MockBean ExcelScheduleService excel;
    @MockBean AuthService auth;

    @BeforeEach void accounts() {
        when(tokens.subject("teacher-token")).thenReturn("GV01");
        when(tokens.subject("admin-token")).thenReturn("QL01");
        when(tokens.subject("invalid")).thenThrow(new JwtException("invalid"));
        when(teachers.findById("GV01")).thenReturn(Optional.of(Teacher.builder()
                .teacherId("GV01").username("teacher").role("TEACHER").build()));
        when(teachers.findById("QL01")).thenReturn(Optional.of(Teacher.builder()
                .teacherId("QL01").username("manager").role("ADMIN").build()));
    }

    @Test void anonymousAndInvalidTokensGet401() throws Exception {
        mvc.perform(get("/api/schedule/week/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/schedule/week/1").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value("ERROR"));
        verifyNoInteractions(schedules);
    }

    @Test void teacherCannotScheduleEvenWithForgedAdminBody() throws Exception {
        mvc.perform(multipart("/api/schedule/auto-generate-excel").file(file())
                .param("role", "ADMIN").header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/schedule/auto-generate-3files").file(file())
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/schedule/rooms").header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(excel);
    }

    @Test void adminCanScheduleButCannotUsePersonalImportOrDelete() throws Exception {
        when(excel.autoScheduleFromExcel(any(), anyString(), anyString(), any()))
                .thenReturn(AutoScheduleResponse.builder().status("SUCCESS").build());
        mvc.perform(multipart("/api/schedule/auto-generate-excel").file(file())
                .header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        mvc.perform(multipart("/api/schedule/upload-excel-teacher").file(file())
                .param("teacherId", "QL01").param("schoolId", "PTIT").param("schoolName", "PTIT")
                .header("Authorization", "Bearer admin-token")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/schedule/school").param("teacherId", "QL01").param("schoolId", "PTIT")
                .header("Authorization", "Bearer admin-token")).andExpect(status().isForbidden());
    }

    @Test void adminCanUseThreeFileSchedulingAndRooms() throws Exception {
        var plan = new MockMultipartFile("planFile", "plan.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        when(excel.autoScheduleFrom3Files(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(AutoScheduleResponse.builder().status("SUCCESS").build());
        mvc.perform(multipart("/api/schedule/auto-generate-3files").file(plan)
                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(excel).autoScheduleFrom3Files(any(), isNull(), isNull(), eq("PTIT"), anyString(), isNull());
        mvc.perform(get("/api/schedule/rooms").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        verify(rooms).findBySchoolCodeOrderByCapacityAsc("PTIT");
    }

    @Test void teacherCanOnlyReadImportAndDeleteOwnSchedule() throws Exception {
        mvc.perform(get("/api/schedule/week/1").param("teacherId", "GV02")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/schedule/upload-excel-teacher").file(file())
                .param("teacherId", "GV02").param("schoolId", "PTIT").param("schoolName", "PTIT")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/schedule/school").param("teacherId", "GV02").param("schoolId", "PTIT")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isForbidden());
        verifyNoInteractions(schedules, excel);
        mvc.perform(multipart("/api/schedule/upload-excel-teacher").file(file())
                .param("teacherId", "GV01").param("schoolId", "PTIT").param("schoolName", "PTIT")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isOk());
        mvc.perform(get("/api/schedule/week/1").param("teacherId", "GV01")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isOk());
        verify(schedules).getWeeklySchedule("GV01", 1);
    }

    @Test void serverRoleOverridesPreviouslyIssuedAdminToken() throws Exception {
        when(teachers.findById("QL01")).thenReturn(Optional.of(Teacher.builder()
                .teacherId("QL01").username("manager").role("TEACHER").build()));
        mvc.perform(multipart("/api/schedule/auto-generate-excel").file(file())
                .header("Authorization", "Bearer admin-token")).andExpect(status().isForbidden());
    }

    @Test void adminCanReadOtherTeachersAndUnknownEndpointsAreDenied() throws Exception {
        mvc.perform(get("/api/schedule/week/1").param("teacherId", "GV02")
                .header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        verify(schedules).getWeeklySchedule("GV02", 1);
        mvc.perform(get("/api/unknown").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "input.xlsx", "application/octet-stream", new byte[]{1}); }

    @Test void passwordChangeRequiresLoginAndIgnoresForgedTargetAccount() throws Exception {
        String body = "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\",\"teacherId\":\"QL01\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/schedule/auth/change-password").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/schedule/auth/change-password").contentType("application/json").content(body)
                .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(auth).changePassword(eq(new AccountPrincipal("GV01", "teacher", "TEACHER")),
                eq(new com.dto.ChangePasswordRequest("old-password", "new-password")));
        mvc.perform(post("/api/schedule/auth/change-password").contentType("application/json").content(body)
                .header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        verify(auth).changePassword(eq(new AccountPrincipal("QL01", "manager", "ADMIN")), any());
    }

    @Test void incorrectCurrentPasswordReturnsFormErrorInsteadOfExpiringSession() throws Exception {
        doThrow(new IllegalArgumentException("Mật khẩu hiện tại không đúng.")).when(auth).changePassword(any(), any());
        mvc.perform(post("/api/schedule/auth/change-password").contentType("application/json")
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password\"}")
                .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng."));
    }

    @Test void removedProvisioningEndpointIsDeniedForAllRoles() throws Exception {
        mvc.perform(post("/api/schedule/auth/provision").contentType("application/json")
                .content("{\"teacherId\":\"GV01\",\"username\":\"teacher\",\"password\":\"password123\"}")
                .header("Authorization", "Bearer teacher-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedule/auth/provision").contentType("application/json")
                .content("{\"teacherId\":\"GV01\",\"username\":\"teacher\",\"password\":\"password123\"}")
                .header("Authorization", "Bearer admin-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedule/auth/provision").contentType("application/json")
                .content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test void errorsUseStableStatusesAndDoNotLeakInternalMessages() throws Exception {
        when(schedules.getWeeklySchedule("GV01", 1)).thenThrow(new com.exception.ResourceNotFoundException("Missing teacher"));
        mvc.perform(get("/api/schedule/week/1").header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value("ERROR"));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("secret SQL")).when(schedules).getWeeklySchedule("GV01", 1);
        mvc.perform(get("/api/schedule/week/1").header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret SQL"))));
        doThrow(new RuntimeException()).when(schedules).getWeeklySchedule("GV01", 1);
        mvc.perform(get("/api/schedule/week/1").header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").isNotEmpty());
    }
}
