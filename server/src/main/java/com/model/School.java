package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "schools")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class School {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false)
    private String schoolCode;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "color_code")
    private String colorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

 @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
private Set<SchoolPeriod> periods = new HashSet<>();

@OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
private Set<TeachingCourse> courses = new HashSet<>();
}