package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

  
    public static final String UNASSIGNED_ID = "__UNASSIGNED__";
    public static final String UNASSIGNED_NAME = "Chưa phân công";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_DEPT_HEAD = "DEPT_HEAD";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_SYSTEM = "SYSTEM";

    public static final String TYPE_HEAD = "TRUONG_BO_MON";
    public static final String TYPE_LECTURER = "GIANG_VIEN";
    public static final String TYPE_ASSISTANT = "TRO_GIANG";
    public static final String TYPE_VISITING = "THINH_GIANG";
    public static final java.util.Set<String> TYPES = java.util.Set.of(TYPE_HEAD, TYPE_LECTURER, TYPE_ASSISTANT, TYPE_VISITING);

    @Id
    @Column(name = "teacher_id", length = 50)
    private String teacherId;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "username", length = 50, unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "role", length = 20)
    @Builder.Default
    private String role = "TEACHER"; 

    @Column(name = "semester_start_date", nullable = false)
    private LocalDate semesterStartDate;


    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "teacher_type", columnDefinition = "VARCHAR(20) DEFAULT 'GIANG_VIEN'")
    @Builder.Default
    private String teacherType = TYPE_LECTURER;

    public boolean unassignedPool() {
        return UNASSIGNED_ID.equals(teacherId);
    }

    public String effectiveType() {
        return teacherType == null || teacherType.isBlank() ? TYPE_LECTURER : teacherType;
    }

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<School> schools = new LinkedHashSet<>();
}