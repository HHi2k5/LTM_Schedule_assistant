package com.service;

import com.dto.AuthRequest;
import com.dto.AuthResponse;
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
import java.time.LocalDate;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TeacherRepository teacherRepository;
    private final TokenService tokens;
    private final PasswordEncoder passwords;

    @Transactional
    public AuthResponse register(AuthRequest req) {
        if (req.getRole() != null && !"TEACHER".equals(req.getRole())) {
            throw new IllegalArgumentException("Đăng ký công khai chỉ dành cho tài khoản giảng viên.");
        }
        if (req.getUsername() == null || req.getUsername().isBlank() ||
            req.getPassword() == null || req.getPassword().isBlank() ||
            req.getTeacherId() == null || req.getTeacherId().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ Tên đăng nhập, Mật khẩu và Mã giảng viên.");
        }

        String username = req.getUsername().trim();
        String teacherId = req.getTeacherId().trim().toUpperCase();

        if (teacherRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập '" + username + "' đã tồn tại!");
        }

        // Nếu mã GV đã được hệ thống xếp lịch tạo trước đó thì cập nhật tài khoản vào mã GV đó
        Teacher teacher = teacherRepository.findById(teacherId).orElseGet(() ->
                Teacher.builder()
                        .teacherId(teacherId)
                        .semesterStartDate(req.getSemesterStartDate() != null ? req.getSemesterStartDate() : LocalDate.of(2026, 8, 10))
                        .build()
        );

        if ("ADMIN".equals(teacher.getRole()) || (teacher.getUsername() != null && !teacher.getUsername().isBlank())) {
            throw new IllegalArgumentException("Mã giảng viên '" + teacherId + "' đã được đăng ký tài khoản!");
        }

        teacher.setUsername(username);
        teacher.setPassword(passwords.encode(req.getPassword()));
        teacher.setFullName(req.getFullName() != null && !req.getFullName().isBlank() ? req.getFullName().trim() : teacherId);
        teacher.setRole("TEACHER");
        if (req.getSemesterStartDate() != null) {
            teacher.setSemesterStartDate(req.getSemesterStartDate());
        }

        teacherRepository.save(teacher);

        return AuthResponse.builder()
                .accessToken(tokens.issue(teacher.getTeacherId()))
                .status("SUCCESS")
                .message("Đăng ký tài khoản thành công!")
                .teacherId(teacher.getTeacherId())
                .fullName(teacher.getFullName())
                .username(teacher.getUsername())
                .role(teacher.getRole())
                .semesterStartDate(teacher.getSemesterStartDate().toString())
                .build();
    }

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
