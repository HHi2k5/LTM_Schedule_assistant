package com.model;

import jakarta.persistence.*;
import lombok.*;

/** Loại phòng (phòng máy, lý thuyết, máy chiếu, thực hành...) và nhóm lớp mà loại phòng đó phục vụ. */
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

    /** THEORY: dùng cho lớp lý thuyết; PRACTICE: dùng cho lớp thực hành. */
    @Column(name = "usage_kind", nullable = false, length = 20)
    private String usageKind;

    public boolean practice() {
        return PRACTICE.equals(usageKind);
    }
}
