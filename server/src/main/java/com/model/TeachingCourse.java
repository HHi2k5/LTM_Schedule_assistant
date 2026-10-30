package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teaching_courses")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TeachingCourse {
    @Column(name = "source_type", length = 20)
    private String sourceType;
    @Column(name = "schedule_class_id", length = 255)
    private String scheduleClassId;
    @Column(name = "schedule_parent_id", length = 255)
    private String scheduleParentId;
    @Column(name = "session_type", length = 20)
    private String sessionType;
    @Column(name = "plan_snapshot", columnDefinition = "LONGTEXT")
    private String planSnapshot;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "classroom")
    private String classroom;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek; // 2: Thứ 2 -> 8: CN

    @Column(name = "start_period", nullable = false)
    private int startPeriod;

    @Column(name = "num_periods", nullable = false)
    private int numPeriods;

    @Column(name = "week_schedule", nullable = false)
    private String weekSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "custom_start_time")
    private java.time.LocalTime customStartTime;

    @Column(name = "custom_end_time")
    private java.time.LocalTime customEndTime;
}
