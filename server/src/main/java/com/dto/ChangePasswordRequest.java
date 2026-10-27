package com.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
