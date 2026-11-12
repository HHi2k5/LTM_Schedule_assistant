package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;


@Entity
@Table(
    name = "school_period_templates",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_period_template_school_num",
        columnNames = {"school_code", "period_number"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolPeriodTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
