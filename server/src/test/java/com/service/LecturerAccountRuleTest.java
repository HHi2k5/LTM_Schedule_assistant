package com.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LecturerAccountRuleTest {
    @Test void vietnameseNamesBecomeLowercaseAsciiWithoutSpacesAndCodeKeepsItsCase() {
        assertEquals("dangthimyleGV01", LecturerAccountService.initialPassword("  Đặng Thị Mỹ-Lệ  ", "gv01"));
        assertEquals("nguyenvananGV02", LecturerAccountService.initialPassword("Nguyễn Văn An", "GV02"));
        assertEquals(LecturerAccountService.initialPassword("Đỗ  Ánh", "A01"), LecturerAccountService.initialPassword("Đo\u0302\u0303 A\u0301nh", "A01"));
    }
    @Test void anUnrepresentableNameOrPasswordBeyondBcryptLimitIsRejectedWithoutTruncation() {
        assertThrows(IllegalArgumentException.class, () -> LecturerAccountService.initialPassword("字字", "GV01"));
        assertThrows(IllegalArgumentException.class, () -> LecturerAccountService.initialPassword("a".repeat(73), "GV01"));
        assertEquals(72, LecturerAccountService.initialPassword("a".repeat(68), "GV01").length());
    }
    @Test void anAdminIdentityCannotBePromotedOrReturnedAsATeacherAccount() {
        var repository = org.mockito.Mockito.mock(com.repository.TeacherRepository.class);
        var service = new LecturerAccountService(repository, org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        var admin = com.model.Teacher.builder().teacherId("ADMIN").username("admin").role("ADMIN").password("unchanged").build();
        assertThrows(IllegalArgumentException.class, () -> service.ensure("GV01", "Nguyễn Văn An", admin, null));
        assertEquals("unchanged", admin.getPassword()); org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
