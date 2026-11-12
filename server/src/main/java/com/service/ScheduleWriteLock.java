package com.service;

import com.model.ScheduleWriteMutex;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Component
public class ScheduleWriteLock {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
       
        entityManager.createNativeQuery("INSERT INTO schedule_write_mutex (id) VALUES (1) ON DUPLICATE KEY UPDATE id = 1").executeUpdate();
        entityManager.find(ScheduleWriteMutex.class, 1, LockModeType.PESSIMISTIC_WRITE);
    }
}
