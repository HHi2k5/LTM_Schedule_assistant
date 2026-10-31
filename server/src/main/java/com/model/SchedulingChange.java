package com.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "scheduling_changes", uniqueConstraints = @UniqueConstraint(name = "uq_change_result_action", columnNames = {"result_version_id", "action"}))
@Getter @Setter @NoArgsConstructor
public class SchedulingChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id", nullable = false, updatable = false) private SchedulingPlan plan;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_version_id", updatable = false) private SchedulingPlanVersion sourceVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "result_version_id", nullable = false, updatable = false) private SchedulingPlanVersion resultVersion;
    @Column(name = "action", nullable = false, length = 20, updatable = false) private String action;
    @Column(name = "actor", nullable = false, length = 50, updatable = false) private String actor;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "affected_entries", nullable = false, updatable = false) private int affectedEntries;
    @Column(name = "reason", nullable = false, length = 255, updatable = false) private String reason;
    @Column(name = "scope_json", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String scopeJson;
}
