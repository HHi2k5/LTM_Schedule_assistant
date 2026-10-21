package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

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
    private String role = "TEACHER"; // TEACHER hoặc ADMIN

    @Column(name = "semester_start_date", nullable = false)
    private LocalDate semesterStartDate;

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<School> schools = new ArrayList<>();
}