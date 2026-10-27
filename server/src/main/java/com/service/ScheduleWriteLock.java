package com.service;

import com.model.ScheduleWriteMutex;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes schedule writes across application instances until the outer transaction ends. */
@Component
public class ScheduleWriteLock {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // A duplicate-key UPDATE takes an exclusive InnoDB lock immediately. INSERT IGNORE
        // takes shared locks which can deadlock when concurrent callers upgrade to FOR UPDATE.
        entityManager.createNativeQuery("INSERT INTO schedule_write_mutex (id) VALUES (1) ON DUPLICATE KEY UPDATE id = 1").executeUpdate();
        entityManager.find(ScheduleWriteMutex.class, 1, LockModeType.PESSIMISTIC_WRITE);
    }
}
