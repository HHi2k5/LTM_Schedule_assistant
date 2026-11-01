package com.service;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleChangePublisherTest {
    private ScheduleEventBroker broker;
    private ScheduleChangePublisher changes;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        broker = mock(ScheduleEventBroker.class);
        changes = new ScheduleChangePublisher(broker);
        var database = new DriverManagerDataSource("jdbc:h2:mem:events;DB_CLOSE_DELAY=-1", "sa", "");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
    }

    @Test
    void publishesOnlyAfterCommitAndCoalescesEveryTeacherInTheTransaction() {
        transaction.executeWithoutResult(status -> {
            changes.changed("A"); changes.changed("B"); changes.changed("A");
            verifyNoInteractions(broker);
        });
        verify(broker, times(1)).publish(Set.of("A", "B"));
        verifyNoMoreInteractions(broker);
    }

    @Test
    void rollbackAndThrownExceptionsNeverPublishOrLeakIntoTheNextTransaction() {
        transaction.executeWithoutResult(status -> { changes.changed("ROLLBACK"); status.setRollbackOnly(); });
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            changes.changed("FAILED"); throw new IllegalStateException("Write failed");
        }));
        verifyNoInteractions(broker);
        transaction.executeWithoutResult(status -> changes.changed("COMMITTED"));
        verify(broker).publish(Set.of("COMMITTED"));
        verifyNoMoreInteractions(broker);
    }

    @Test
    void aNewInnerTransactionDoesNotPublishTheUncommittedOuterTeachers() {
        var inner = new TransactionTemplate(transaction.getTransactionManager());
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            changes.changed("OUTER");
            inner.executeWithoutResult(nested -> changes.changed("INNER"));
            verify(broker).publish(Set.of("INNER"));
            verifyNoMoreInteractions(broker);
            status.setRollbackOnly();
        });
        verifyNoMoreInteractions(broker);
    }

    @Test
    void emptyChangesDoNotProduceNotifications() {
        transaction.executeWithoutResult(status -> { changes.changed(null); changes.changed(" "); });
        verifyNoInteractions(broker);
    }
}
