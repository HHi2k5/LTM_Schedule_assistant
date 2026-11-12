package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;


@Entity
@Table(name = "teacher_unavailability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "teacher_id", nullable = false, length = 50)
    private String teacherId;


    @Column(name = "day_of_week")
    private Integer dayOfWeek;

  
    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "note", length = 255)
    private String note;
}
