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
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom TEACHER_ID_RANDOM = new SecureRandom();
    private static final String TEACHER_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int MAX_TEACHER_ID_ATTEMPTS = 100;

    private final TeacherRepository teacherRepository;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final ScheduleWriteLock writeLock;

    @Transactional
    public AuthResponse register(AuthRequest req) {
        writeLock.acquire();
        if (req == null) throw new IllegalArgumentException("Thiếu thông tin đăng ký.");
        if (req.getRole() != null && !"TEACHER".equals(req.getRole())) {
            throw new IllegalArgumentException("Đăng ký công khai chỉ dành cho tài khoản giảng viên.");
        }
        if (req.getUsername() == null || req.getUsername().isBlank() ||
            req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        }

        String username = req.getUsername().trim();
        if (req.getTeacherId() != null && !req.getTeacherId().isBlank()
                && teacherRepository.findById(req.getTeacherId().trim().toUpperCase(java.util.Locale.ROOT)).isPresent()) {
            throw new IllegalArgumentException("Không thể đăng ký bằng mã hồ sơ đã tồn tại. Tài khoản mới dùng mã do hệ thống cấp.");
        }
        ScheduleValidation.text(username, 50, "Tên đăng nhập");
        if (req.getPassword().length() < 8 || req.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
        }
        if (req.getFullName() != null && req.getFullName().length() > 100) throw new IllegalArgumentException("Họ tên quá dài.");

        if (teacherRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập '" + username + "' đã tồn tại!");
        }

        LocalDate startDate = req.getSemesterStartDate() != null ? req.getSemesterStartDate() : LocalDate.of(2026, 8, 10);
        ScheduleValidation.monday(startDate);
        String teacherId = generateTeacherId();
        Teacher teacher = Teacher.builder()
                        .teacherId(teacherId)
                        .semesterStartDate(startDate).build();

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

    private String generateTeacherId() {
        // register() holds the database write lock until this account is committed.
        for (int attempt = 0; attempt < MAX_TEACHER_ID_ATTEMPTS; attempt++) {
            var id = new StringBuilder("GV");
            for (int i = 0; i < 4; i++) {
                id.append(TEACHER_ID_ALPHABET.charAt(TEACHER_ID_RANDOM.nextInt(TEACHER_ID_ALPHABET.length())));
            }
            String candidate = id.toString();
            if (teacherRepository.findById(candidate).isEmpty()) return candidate;
        }
        throw new IllegalArgumentException("Chưa tạo được mã giảng viên mới. Vui lòng thử đăng ký lại.");
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
