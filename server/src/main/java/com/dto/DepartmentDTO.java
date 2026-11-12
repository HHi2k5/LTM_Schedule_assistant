package com.dto;


public record DepartmentDTO(Long id, String schoolCode, String code, String name,
                            String headTeacherId, String headTeacherName,
                            long subjectCount, long teacherCount) {}
