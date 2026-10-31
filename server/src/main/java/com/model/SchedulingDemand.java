package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scheduling_demands", uniqueConstraints = @UniqueConstraint(name = "uq_version_demand", columnNames = {"version_id", "demand_index"}))
@Getter @Setter @NoArgsConstructor
public class SchedulingDemand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "version_id", nullable = false, updatable = false) private SchedulingPlanVersion version;
    @Column(name = "demand_index", nullable = false, updatable = false) private int demandIndex;
    @Column(name = "course_code", nullable = false, length = 50, updatable = false) private String courseCode;
    @Column(name = "cohort", nullable = false, length = 50, updatable = false) private String cohort;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String snapshotJson;
}
