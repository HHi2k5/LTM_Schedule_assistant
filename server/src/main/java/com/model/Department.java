package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(name = "uq_department_school_code", columnNames = {"school_code", "department_code"}))
@Getter @Setter @NoArgsConstructor
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_code", nullable = false)
    private Institution institution;
    @Column(name = "department_code", nullable = false, length = 50)
    private String departmentCode;
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;
}
