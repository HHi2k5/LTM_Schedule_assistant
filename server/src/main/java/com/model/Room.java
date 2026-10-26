package com.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, unique = true, length = 50)
    private String roomCode;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "room_type", nullable = false, length = 30)
    private String roomType; // THEORY_LARGE, THEORY_MEDIUM, THEORY_STANDARD, LAB_PRACTICE

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "is_lab", nullable = false)
    private boolean isLab;

    @Column(name = "building", length = 50)
    private String building;

    @Column(name = "school_code", length = 50)
    private String schoolCode;
}