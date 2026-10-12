package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teachers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Teacher {
    @Id
    @Column(name = "teacher_id", length = 50)
    private String teacherId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "semester_start_date", nullable = false)
    private LocalDate semesterStartDate;

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<School> schools = new ArrayList<>();
}