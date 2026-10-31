package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "scheduling_entries", uniqueConstraints = {@UniqueConstraint(name = "uq_version_class_session", columnNames = {"version_id", "schedule_class_id", "lesson_date", "start_period"}), @UniqueConstraint(name = "uq_version_entry_key", columnNames = {"version_id", "entry_key"})})
@Getter @Setter @NoArgsConstructor
public class SchedulingEntry {
    @Column(name = "entry_key", nullable = false, length = 50, updatable = false) private String entryKey;
    @Column(name = "locked", nullable = false, updatable = false) private boolean locked;
    @PrePersist void assignKey() { if (entryKey == null) entryKey = java.util.UUID.randomUUID().toString(); }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false, updatable = false) private SchedulingPlanVersion version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "demand_id", nullable = false, updatable = false) private SchedulingDemand demand;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lecturer_id", nullable = false, updatable = false) private InstitutionLecturer lecturer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false, updatable = false) private Room room;
    @Column(name = "lesson_date", nullable = false, updatable = false) private LocalDate lessonDate;
    @Column(name = "start_time", nullable = false, updatable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false, updatable = false) private LocalTime endTime;
    @Column(name = "start_period", nullable = false, updatable = false) private int startPeriod;
    @Column(name = "num_periods", nullable = false, updatable = false) private int numPeriods;
    @Column(name = "schedule_class_id", nullable = false, length = 255, updatable = false) private String scheduleClassId;
    @Column(name = "schedule_parent_id", nullable = false, length = 255, updatable = false) private String scheduleParentId;
    @Column(name = "class_group", nullable = false, length = 100, updatable = false) private String classGroup;
    @Column(name = "session_type", nullable = false, length = 20, updatable = false) private String sessionType;
    @Column(name = "student_count", nullable = false, updatable = false) private int studentCount;
    @Column(name = "course_name", nullable = false, length = 255, updatable = false) private String courseName;
    @Column(name = "lecturer_name", nullable = false, length = 100, updatable = false) private String lecturerName;
    @Column(name = "room_code", nullable = false, length = 50, updatable = false) private String roomCode;
}
