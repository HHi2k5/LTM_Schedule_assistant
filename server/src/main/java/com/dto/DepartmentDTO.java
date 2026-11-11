package com.dto;

/** Bộ môn kèm trưởng bộ môn hiện tại và số môn/giảng viên đang thuộc bộ môn. */
public record DepartmentDTO(Long id, String schoolCode, String code, String name,
                            String headTeacherId, String headTeacherName,
                            long subjectCount, long teacherCount) {}
