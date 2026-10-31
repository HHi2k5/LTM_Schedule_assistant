package com.service;

import com.dto.AuthRequest;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
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
    @BeforeEach void setup() { auth = new AuthService(teachers, tokens, passwords, mock(ScheduleWriteLock.class)); }

    @Test void existingLongTeacherIdsStillLoginWithoutBeingShortened() {
        String id = "GV_1234567890ABCDEF1234567890ABCDEF";
        var teacher = account("TEACHER"); teacher.setTeacherId(id);
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(teacher));
        var response = auth.login(request()); assertEquals(id, response.getTeacherId()); assertEquals(id, tokens.subject(response.getAccessToken()));
        verify(teachers, never()).save(any());
    }
    @Test void existingSha256PasswordMigratesOnLoginWithoutReset() throws Exception {
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("test-password".getBytes(StandardCharsets.UTF_8)));
        var teacher = account("TEACHER"); teacher.setPassword(oldHash);
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(teacher));
        assertEquals("GV01", tokens.subject(auth.login(request()).getAccessToken()));
        assertTrue(passwords.matches("test-password", teacher.getPassword())); assertNotEquals(oldHash, teacher.getPassword());
    }
    @Test void unknownUsernameCannotLogInOrCreateAnAccount() {
        assertThrows(BadCredentialsException.class, () -> auth.login(request())); verify(teachers, never()).save(any());
    }
    @Test void wrongPasswordCannotLogInOrChangeTheStoredAccount() {
        var teacher = account("TEACHER"); String hash = teacher.getPassword();
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(teacher)); var request = request(); request.setPassword("wrong-password");
        assertThrows(BadCredentialsException.class, () -> auth.login(request)); assertEquals(hash, teacher.getPassword()); verify(teachers, never()).save(any());
    }
    @Test void aCorrectPasswordCannotGrantAnUnassignedRole() {
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(account("STUDENT")));
        assertThrows(BadCredentialsException.class, () -> auth.login(request())); verify(teachers, never()).save(any());
    }
    private Teacher account(String role) { return Teacher.builder().teacherId("GV01").username("teacher").role(role).password(passwords.encode("test-password")).semesterStartDate(LocalDate.of(2026, 8, 10)).build(); }
    private AuthRequest request() { var request = new AuthRequest(); request.setUsername("teacher"); request.setPassword("test-password"); return request; }
}
