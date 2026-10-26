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
        verify(rooms).findAllByOrderByCapacityAsc();
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
}
