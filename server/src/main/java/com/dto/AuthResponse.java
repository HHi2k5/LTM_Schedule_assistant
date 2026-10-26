package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String status;
    private String message;
    private String teacherId;
    private String fullName;
    private String username;
    private String role;
    private String semesterStartDate;
}
