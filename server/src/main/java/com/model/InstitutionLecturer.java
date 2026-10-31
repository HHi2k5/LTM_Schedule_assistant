package com.model;

import jakarta.persistence.*;
import lombok.*;

/** Directory membership does not grant access or create a login account. */
@Entity
@Table(name = "institution_lecturers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lecturer_school_code", columnNames = {"school_code", "lecturer_code"}),
        @UniqueConstraint(name = "uq_lecturer_school_account", columnNames = {"school_code", "account_teacher_id"}),
        @UniqueConstraint(name = "uq_lecturer_school_schedule", columnNames = {"school_code", "schedule_teacher_id"})})
@Getter @Setter @NoArgsConstructor
public class InstitutionLecturer {
    public enum LecturerType { STAFF, VISITING }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_code", nullable = false)
    private Institution institution;
    @Column(name = "lecturer_code", nullable = false, length = 50)
    private String lecturerCode;
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    @Enumerated(EnumType.STRING)
    @Column(name = "lecturer_type", nullable = false, length = 20)
    private LecturerType lecturerType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_teacher_id")
    private Teacher account;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_teacher_id")
    private Teacher scheduleTeacher;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;
}
