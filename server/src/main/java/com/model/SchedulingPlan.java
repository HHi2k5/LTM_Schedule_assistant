package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduling_plans", uniqueConstraints = @UniqueConstraint(name = "uq_plan_scope", columnNames = {"school_code", "department_id", "semester_key", "semester_start_date"}))
@Getter @Setter @NoArgsConstructor
public class SchedulingPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_code", nullable = false) private Institution institution;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false) private Department department;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "semester_key", nullable = false, length = 80) private String semesterKey;
    @Column(name = "semester_start_date", nullable = false) private LocalDate semesterStartDate;
    @Column(name = "version_sequence", nullable = false) private int versionSequence;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "draft_version_id") private SchedulingPlanVersion draftVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "confirmed_version_id") private SchedulingPlanVersion confirmedVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version @Column(name = "revision", nullable = false) private long revision;
}
