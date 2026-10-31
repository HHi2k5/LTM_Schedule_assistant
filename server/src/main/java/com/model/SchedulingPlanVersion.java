package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Input and computed draft are immutable; publication records the resolved account identities. */
@Entity
@Table(name = "scheduling_plan_versions", uniqueConstraints = @UniqueConstraint(name = "uq_plan_version", columnNames = {"plan_id", "version_number"}))
@Getter @Setter @NoArgsConstructor
public class SchedulingPlanVersion {
    @Column(name = "adjustment_json", columnDefinition = "LONGTEXT", updatable = false) private String adjustmentJson;
    @Column(name = "customized", nullable = false, updatable = false) private boolean customized;
    @Column(name = "visiting_change_json", columnDefinition = "LONGTEXT", updatable = false) private String visitingChangeJson;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id", nullable = false, updatable = false) private SchedulingPlan plan;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", nullable = false, length = 50, updatable = false) private String createdBy;
    @Column(name = "complete", nullable = false, updatable = false) private boolean complete;
    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String requestJson;
    @Column(name = "configuration_json", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String configurationJson;
    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String resultJson;
    @Column(name = "publication_json", columnDefinition = "LONGTEXT") private String publicationJson;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "confirmed_by", length = 50) private String confirmedBy;
}
