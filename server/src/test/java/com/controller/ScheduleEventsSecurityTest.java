package com.controller;

import com.config.SecurityConfig;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.*;
import com.service.ScheduleEventBroker;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduleEventsController.class)
@Import({SecurityConfig.class, TokenFilter.class})
@ActiveProfiles("test")
class ScheduleEventsSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ScheduleEventBroker broker;
    @MockBean TokenService tokens;
    @MockBean TeacherRepository teachers;
    private final Instant expiry = Instant.now().plusSeconds(600);

    @BeforeEach
    void setUp() {
        when(tokens.subject("teacher-token")).thenReturn("GV01");
        when(tokens.expiresAt("teacher-token")).thenReturn(expiry);
        when(teachers.findById("GV01")).thenReturn(Optional.of(Teacher.builder().teacherId("GV01").username("teacher").role("TEACHER").build()));
    }

    @Test
    void anUnauthenticatedOrQueryTokenOnlyConnectionIsRejected() throws Exception {
        mvc.perform(get("/api/schedule/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/schedule/events").param("token", "teacher-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(broker);
    }

    @Test
    void theAuthenticatedTeacherCannotChooseAnotherTeachersStream() throws Exception {
        var emitter = new SseEmitter(60000L);
        emitter.send(SseEmitter.event().name("ready").data(Map.of("revision", 0)));
        when(broker.subscribe(any(), any())).thenReturn(emitter);
        var result = mvc.perform(get("/api/schedule/events").header("Authorization", "Bearer teacher-token")
                        .param("teacherId", "SOMEONE_ELSE"))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        verify(broker).subscribe(new AccountPrincipal("GV01", "teacher", "TEACHER"), expiry);
        emitter.complete();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void anExpiredOrInvalidTokenIsRejectedBeforeTheStreamOpens() throws Exception {
        when(tokens.subject("expired")).thenThrow(new JwtException("Expired"));
        mvc.perform(get("/api/schedule/events").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(broker);
    }
}
