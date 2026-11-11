package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCourseDTO {
    private Long id;
    private String schoolId;
    private String schoolName;
    private String courseCode;
    private String courseName;
    private String room;
    private int dayOfWeek;
    private int startPeriod;
    private int numPeriods;
    private String weekSchedule;
    private String sourceType;
    /** true = lịch do khoa xếp/phân (WEEKLY_AUTO, VISITING_AUTO) – không được xóa từ phía giảng viên. */
    private boolean departmentAssigned;
}
