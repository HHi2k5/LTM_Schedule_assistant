package com.service;

import com.dto.AuthRequest;
import com.dto.AuthResponse;
import com.model.Teacher;
import com.repository.TeacherRepository;
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

    @Transactional
    public AuthResponse register(AuthRequest req) {
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

        if (teacher.getUsername() != null && !teacher.getUsername().isBlank()) {
            throw new IllegalArgumentException("Mã giảng viên '" + teacherId + "' đã được đăng ký tài khoản!");
        }

        teacher.setUsername(username);
        teacher.setPassword(hashPassword(req.getPassword()));
        teacher.setFullName(req.getFullName() != null && !req.getFullName().isBlank() ? req.getFullName().trim() : teacherId);
        teacher.setRole(req.getRole() != null ? req.getRole() : "TEACHER");
        if (req.getSemesterStartDate() != null) {
            teacher.setSemesterStartDate(req.getSemesterStartDate());
        }

        teacherRepository.save(teacher);

        return AuthResponse.builder()
                .status("SUCCESS")
                .message("Đăng ký tài khoản thành công!")
                .teacherId(teacher.getTeacherId())
                .fullName(teacher.getFullName())
                .username(teacher.getUsername())
                .role(teacher.getRole())
                .semesterStartDate(teacher.getSemesterStartDate().toString())
                .build();
    }

    public AuthResponse login(AuthRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        }

        Teacher teacher = teacherRepository.findByUsername(req.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("Sai tên đăng nhập hoặc mật khẩu!"));

        String hashedInput = hashPassword(req.getPassword());
        if (!hashedInput.equals(teacher.getPassword())) {
            throw new IllegalArgumentException("Sai tên đăng nhập hoặc mật khẩu!");
        }

        return AuthResponse.builder()
                .status("SUCCESS")
                .message("Đăng nhập thành công!")
                .teacherId(teacher.getTeacherId())
                .fullName(teacher.getFullName())
                .username(teacher.getUsername())
                .role(teacher.getRole())
                .semesterStartDate(teacher.getSemesterStartDate().toString())
                .build();
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