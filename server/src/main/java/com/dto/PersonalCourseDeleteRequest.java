package com.dto;

import java.util.List;

public record PersonalCourseDeleteRequest(String teacherId, List<Long> courseIds) {}
