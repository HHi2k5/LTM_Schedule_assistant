package com.service;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ScheduleChangePublisher {
    private final ScheduleEventBroker events;

    public void changed(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publish(Set.of(teacherId));
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> affected = (Set<String>) TransactionSynchronizationManager.getResource(this);
        if (affected == null) {
            affected = new LinkedHashSet<>();
            Set<String> transactionTeachers = affected;
            TransactionSynchronizationManager.bindResource(this, affected);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { events.publish(Set.copyOf(transactionTeachers)); }
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(ScheduleChangePublisher.this); }
                @Override public void suspend() { TransactionSynchronizationManager.unbindResource(ScheduleChangePublisher.this); }
                @Override public void resume() { TransactionSynchronizationManager.bindResource(ScheduleChangePublisher.this, transactionTeachers); }
            });
        }
        affected.add(teacherId);
    }
}
