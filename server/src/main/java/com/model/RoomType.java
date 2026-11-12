package com.model;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "room_types", uniqueConstraints = @UniqueConstraint(name = "uq_room_type_school_code", columnNames = {"school_code", "code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {
    public static final String THEORY = "THEORY";
    public static final String PRACTICE = "PRACTICE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

  
    @Column(name = "usage_kind", nullable = false, length = 20)
    private String usageKind;

    public boolean practice() {
        return PRACTICE.equals(usageKind);
    }
}
