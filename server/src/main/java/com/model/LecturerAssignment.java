package com.model;

import jakarta.persistence.*;
import lombok.*;

/** Reusable eligibility and default weights, not a scheduled class. */
@Entity
@Table(name = "lecturer_assignments", uniqueConstraints = @UniqueConstraint(name = "uq_lecturer_course_cohort", columnNames = {"lecturer_id", "course_code", "cohort"}))
@Getter @Setter @NoArgsConstructor
public class LecturerAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private InstitutionLecturer lecturer;
    @Column(name = "course_code", nullable = false, length = 50)
    private String courseCode;
    @Column(name = "course_name", nullable = false, length = 180)
    private String courseName;
    @Column(name = "cohort", nullable = false, length = 50)
    private String cohort;
    @Column(name = "theory_weight", nullable = false)
    private int theoryWeight;
    @Column(name = "practice_weight", nullable = false)
    private int practiceWeight;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;
}
