package com.model;

import jakarta.persistence.*;

@Entity
@Table(name = "schedule_write_mutex")
public class ScheduleWriteMutex {
    @Id
    private Integer id;
}
