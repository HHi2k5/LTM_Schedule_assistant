package com.service;

import com.dto.AuthRequest;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDate;
import java.util.Optional;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final TokenService tokens = new TokenService("", 3600);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private AuthService auth;

    @BeforeEach void setup() { auth = new AuthService(teachers, tokens, passwords); }

    @Test void publicRegistrationCannotAssignAdmin() {
        AuthRequest request = request(); request.setRole("ADMIN");
        assertThrows(IllegalArgumentException.class, () -> auth.register(request));
        verifyNoInteractions(teachers);
    }

    @Test void registrationCreatesTeacherWithTokenAndHashedPassword() {
        when(teachers.findById("GV01")).thenReturn(Optional.empty());
        var response = auth.register(request());
        var capture = org.mockito.ArgumentCaptor.forClass(Teacher.class);
        verify(teachers).save(capture.capture());
        assertEquals("TEACHER", capture.getValue().getRole());
        assertTrue(passwords.matches("test-password", capture.getValue().getPassword()));
        assertEquals("GV01", tokens.subject(response.getAccessToken()));
    }

    @Test void cannotClaimReservedAdminAccount() {
        when(teachers.findById("GV01")).thenReturn(Optional.of(Teacher.builder().teacherId("GV01").role("ADMIN").build()));
        assertThrows(IllegalArgumentException.class, () -> auth.register(request()));
        verify(teachers, never()).save(any());
    }

    @Test void existingSha256PasswordMigratesOnLoginWithoutReset() throws Exception {
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("test-password".getBytes(StandardCharsets.UTF_8)));
        Teacher teacher = Teacher.builder().teacherId("GV01").username("teacher").role("TEACHER")
                .password(oldHash).semesterStartDate(LocalDate.of(2026, 8, 10)).build();
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(teacher));
        assertEquals("GV01", tokens.subject(auth.login(request()).getAccessToken()));
        assertTrue(passwords.matches("test-password", teacher.getPassword()));
        assertNotEquals(oldHash, teacher.getPassword());
    }

    private AuthRequest request() {
        var request = new AuthRequest(); request.setUsername("teacher"); request.setPassword("test-password");
        request.setTeacherId("gv01"); request.setFullName("Teacher"); return request;
    }
}
