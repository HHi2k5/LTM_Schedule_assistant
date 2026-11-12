package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(name = "uq_subject_school_code", columnNames = {"school_code", "course_code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "course_code", nullable = false, length = 50)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 180)
    private String courseName;


    @Column(name = "theory_room_type", length = 30)
    private String theoryRoomType;

  
    @Column(name = "practice_room_type", length = 30)
    private String practiceRoomType;
}
