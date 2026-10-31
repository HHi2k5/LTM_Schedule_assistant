package com.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "lecturer_busy_slots", uniqueConstraints = @UniqueConstraint(name = "uq_version_busy", columnNames = {"version_id", "lecturer_id", "busy_date", "start_time", "end_time"}))
@Getter @Setter @NoArgsConstructor
public class LecturerBusySlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false, updatable = false) private SchedulingPlanVersion version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lecturer_id", nullable = false, updatable = false) private InstitutionLecturer lecturer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_teacher_id") private Teacher scheduleTeacher;
    @Column(name = "busy_date", nullable = false, updatable = false) private LocalDate busyDate;
    @Column(name = "start_time", nullable = false, updatable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false, updatable = false) private LocalTime endTime;
    @Column(name = "reason", nullable = false, length = 255, updatable = false) private String reason;
}
