package com.model;

import jakarta.persistence.*;
import lombok.*;

/** Shared directory; School remains the legacy per-teacher schedule container. */
@Entity
@Table(name = "institutions")
@Getter @Setter @NoArgsConstructor
public class Institution {
    @Id
    @Column(name = "school_code", length = 50)
    private String schoolCode;
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;
}
