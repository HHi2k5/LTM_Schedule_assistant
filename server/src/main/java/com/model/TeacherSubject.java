package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "teacher_subjects",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_teacher_subject_school",
        columnNames = {"school_code", "teacher_id", "course_code", "cohort"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "teacher_id", nullable = false, length = 50)
    private String teacherId;

    @Column(name = "teacher_name", nullable = false, length = 100)
    private String teacherName;

    @Column(name = "course_code", nullable = false, length = 50)
    private String courseCode;

    @Column(name = "course_name", length = 180)
    private String courseName;

    @Column(name = "cohort", nullable = false, length = 50)
    @Builder.Default
    private String cohort = "*"; // "*" nghĩa là dạy mọi khóa của môn này, hoặc ghi rõ "2024"

    @Column(name = "theory_weight", nullable = false)
    @Builder.Default
    private Integer theoryWeight = 1;

    @Column(name = "practice_weight", nullable = false)
    @Builder.Default
    private Integer practiceWeight = 1;
}