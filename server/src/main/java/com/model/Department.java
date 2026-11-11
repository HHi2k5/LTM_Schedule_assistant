package com.model;

import jakarta.persistence.*;
import lombok.*;

/** Bộ môn của khoa (VD: Công nghệ phần mềm, Hệ thống thông tin). */
@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(name = "uq_department_school_code", columnNames = {"school_code", "code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
