package com.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AuthRequest {
    private String username;
    private String password;
    private String teacherId;   // Dùng khi đăng ký (VD: GV01)
    private String fullName;    // Họ và tên
    private String role;        // TEACHER hoặc ADMIN
    private LocalDate semesterStartDate;
}