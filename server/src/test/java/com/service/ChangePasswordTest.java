package com.service;

import com.dto.AuthRequest;
import com.dto.ChangePasswordRequest;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.AccountPrincipal;
import com.security.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChangePasswordTest {
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ScheduleWriteLock lock = mock(ScheduleWriteLock.class);
    private final AuthService auth = new AuthService(teachers, new TokenService("", 3600), encoder, lock);
    private final AccountPrincipal account = new AccountPrincipal("GV01", "teacher", "TEACHER");

    private Teacher existing(String role, String encoded) {
        var teacher = Teacher.builder().teacherId("GV01").username("teacher").role(role).password(encoded)
                .semesterStartDate(LocalDate.of(2026, 8, 10)).build();
        when(teachers.findById("GV01")).thenReturn(Optional.of(teacher));
        when(teachers.findByUsername("teacher")).thenReturn(Optional.of(teacher));
        return teacher;
    }

    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN"})
    void changesOnlyAuthenticatedAccountAndNewPasswordWorksForLogin(String role) {
        var teacher = existing(role, encoder.encode("old-password"));
        String before = teacher.getPassword();
        auth.changePassword(new AccountPrincipal("GV01", "teacher", role), new ChangePasswordRequest("old-password", "new-password"));
        assertNotEquals(before, teacher.getPassword());
        assertTrue(encoder.matches("new-password", teacher.getPassword()));
        assertFalse(encoder.matches("old-password", teacher.getPassword()));
        assertEquals(role, teacher.getRole());
        assertEquals(LocalDate.of(2026, 8, 10), teacher.getSemesterStartDate());
        var order = inOrder(lock, teachers);
        order.verify(lock).acquire(); order.verify(teachers).findById("GV01"); order.verify(teachers).save(teacher);
        var login = new AuthRequest(); login.setUsername("teacher"); login.setPassword("new-password");
        assertEquals(role, auth.login(login).getRole());
        login.setPassword("old-password");
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> auth.login(login));
        assertThrows(IllegalArgumentException.class, () -> auth.changePassword(account, new ChangePasswordRequest("old-password", "other-password")));
        verify(teachers, times(1)).save(any());
    }

    @Test void wrongCurrentPasswordDoesNotWrite() {
        var teacher = existing("TEACHER", encoder.encode("old-password"));
        String before = teacher.getPassword();
        assertThrows(IllegalArgumentException.class, () -> auth.changePassword(account, new ChangePasswordRequest("wrong-password", "new-password")));
        assertEquals(before, teacher.getPassword()); verify(teachers, never()).save(any());
    }

    @Test void validatesNewPasswordBytesAndRejectsReuseOrMissingInput() {
        existing("TEACHER", encoder.encode("old-password"));
        for (var request : new ChangePasswordRequest[] { null, new ChangePasswordRequest(null, "new-password"),
                new ChangePasswordRequest("old-password", null), new ChangePasswordRequest("old-password", "short"),
                new ChangePasswordRequest("old-password", "ế".repeat(25)), new ChangePasswordRequest("old-password", "old-password") }) {
            assertThrows(IllegalArgumentException.class, () -> auth.changePassword(account, request));
        }
        verify(teachers, never()).save(any());
        auth.changePassword(account, new ChangePasswordRequest("old-password", "ế".repeat(24)));
        verify(teachers).save(any());
    }

    @Test void supportsLegacyHashesAndRequiresPrincipal() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("old-password".getBytes(StandardCharsets.UTF_8)));
        var teacher = existing("TEACHER", hash);
        auth.changePassword(account, new ChangePasswordRequest("old-password", "new-password"));
        assertTrue(encoder.matches("new-password", teacher.getPassword()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> auth.changePassword(null, new ChangePasswordRequest("old-password", "new-password")));
    }
}
