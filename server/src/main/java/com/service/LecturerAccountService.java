package com.service;

import com.model.Teacher;
import com.dto.CatalogDTO.LecturerView;
import com.dto.CatalogSchedulingDTO.LecturerIdentity;
import com.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.text.Normalizer;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Invoked only by authorized writes; previews never call this service. */
@Service @RequiredArgsConstructor
public class LecturerAccountService {
    private final TeacherRepository teachers;
    private final PasswordEncoder passwords;

    static String initialPassword(String fullName, String lecturerCode) {
        String name = Normalizer.normalize(CatalogService.text(fullName, 100, "Họ tên"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('Đ', 'D').replace('đ', 'd').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (name.isEmpty()) throw new IllegalArgumentException("Họ tên cần có chữ Latin để tạo mật khẩu không dấu.");
        String result = name + CatalogService.code(lecturerCode, "Mã GV");
        if (result.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Tên không dấu cộng mã GV vượt 72 byte; rút gọn họ tên hoặc mã GV trước khi tạo tài khoản.");
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Teacher ensure(String lecturerCode, String fullName, Teacher identity, LocalDate start) {
        if (identity != null && !"TEACHER".equals(identity.getRole())) throw new IllegalArgumentException("Chỉ tạo tài khoản cho hồ sơ giảng viên.");
        if (identity != null && identity.getUsername() != null && !identity.getUsername().isBlank()) return identity;
        String code = CatalogService.code(lecturerCode, "Mã GV");
        checkAvailable(code, fullName, identity == null ? null : identity.getTeacherId());
        var account = identity == null ? Teacher.builder().teacherId(code).semesterStartDate(start == null ? LocalDate.of(2026, 8, 10) : start).build() : identity;
        if (!"TEACHER".equals(account.getRole())) throw new IllegalArgumentException("Chỉ tạo tài khoản cho hồ sơ giảng viên.");
        account.setFullName(CatalogService.text(fullName, 100, "Họ tên")); account.setUsername(code);
        account.setPassword(passwords.encode(initialPassword(fullName, code))); account.setAutoProvisioned(true);
        return teachers.saveAndFlush(account);
    }

    void checkAvailable(String lecturerCode, String fullName, String identityId) {
        String code = CatalogService.code(lecturerCode, "Mã GV"); initialPassword(fullName, code);
        var login = teachers.findByUsernameIgnoreCase(code);
        if (login.isPresent() && !Objects.equals(identityId, login.get().getTeacherId()))
            throw new IllegalArgumentException("Tên đăng nhập " + code + " đã tồn tại; liên kết đúng mã tài khoản GV, không tạo hoặc gộp tự động.");
        if (identityId == null && teachers.existsById(code))
            throw new IllegalArgumentException("Mã GV " + code + " đã có hồ sơ lịch; khai báo hồ sơ lịch cũ hoặc liên kết đúng tài khoản trước.");
    }

    static boolean compatible(LecturerIdentity original, LecturerView current) {
        if (Objects.equals(original.accountTeacherId(), current.accountTeacherId())) return true;
        return original.accountTeacherId() == null && current.autoProvisioned() && current.accountTeacherId() != null
                && current.accountTeacherId().equals(current.scheduleTeacherId()) && current.lecturerCode().equals(current.accountUsername())
                && (original.teacherId().startsWith("DRAFT_") || original.teacherId().equals(current.accountTeacherId()));
    }
}
