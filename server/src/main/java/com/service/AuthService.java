package com.service;

import com.dto.AuthRequest;
import com.dto.AuthResponse;
import com.dto.ChangePasswordRequest;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.TokenService;
import com.security.AccountPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {


    private final TeacherRepository teacherRepository;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final ScheduleWriteLock writeLock;

    @Transactional
    public AuthResponse login(AuthRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        }

        Teacher teacher = teacherRepository.findByUsername(req.getUsername().trim())
                .orElseThrow(() -> new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu!"));

        String savedPassword = teacher.getPassword();
        boolean legacy = savedPassword != null && savedPassword.matches("[0-9a-f]{64}");
        if (savedPassword == null || !(legacy ? hashPassword(req.getPassword()).equals(savedPassword)
                : passwords.matches(req.getPassword(), savedPassword))) {
            throw new BadCredentialsException("Sai tên đăng nhập hoặc mật khẩu!");
        }
        if (legacy) { teacher.setPassword(passwords.encode(req.getPassword())); teacherRepository.save(teacher); }
        if (!"TEACHER".equals(teacher.getRole()) && !"ADMIN".equals(teacher.getRole())) {
            throw new BadCredentialsException("Tài khoản chưa được cấp vai trò hợp lệ.");
        }

        return AuthResponse.builder()
                .accessToken(tokens.issue(teacher.getTeacherId()))
                .status("SUCCESS")
                .message("Đăng nhập thành công!")
                .teacherId(teacher.getTeacherId())
                .fullName(teacher.getFullName())
                .username(teacher.getUsername())
                .role(teacher.getRole())
                .semesterStartDate(teacher.getSemesterStartDate().toString())
                .build();
    }

    public AuthResponse current(AccountPrincipal account) {
        Teacher teacher = teacherRepository.findById(account.teacherId()).orElseThrow();
        return AuthResponse.builder().status("SUCCESS").teacherId(teacher.getTeacherId())
                .fullName(teacher.getFullName()).username(teacher.getUsername()).role(teacher.getRole())
                .semesterStartDate(teacher.getSemesterStartDate().toString()).build();
    }

    @Transactional
    public void changePassword(AccountPrincipal account, ChangePasswordRequest request) {
        if (account == null) throw new org.springframework.security.access.AccessDeniedException("Cần đăng nhập để đổi mật khẩu.");
        if (request == null || request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new IllegalArgumentException("Nhập mật khẩu hiện tại.");
        }
        String next = request.newPassword();
        if (next == null || next.isBlank() || next.length() < 8 || next.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
        }
        // Serialize account writes so two requests cannot both reuse the old password.
        writeLock.acquire();
        Teacher teacher = teacherRepository.findById(account.teacherId())
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không còn tồn tại."));
        if (!matchesPassword(request.currentPassword(), teacher.getPassword())) {
            // A wrong current password is a form error, not an expired bearer session.
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        if (matchesPassword(next, teacher.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        teacher.setPassword(passwords.encode(next));
        teacherRepository.save(teacher);
    }

    private boolean matchesPassword(String raw, String encoded) {
        if (encoded == null) return false;
        return encoded.matches("[0-9a-f]{64}") ? hashPassword(raw).equals(encoded) : passwords.matches(raw, encoded);
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa mật khẩu", e);
        }
    }
}
