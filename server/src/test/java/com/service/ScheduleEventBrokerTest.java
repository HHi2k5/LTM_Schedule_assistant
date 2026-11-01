package com.service;

import com.security.AccountPrincipal;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleEventBrokerTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private ScheduleEventBroker broker;
    private ScheduledExecutorService timer;
    private Queue<SseEmitter> emitters;

    @BeforeEach
    void setUp() {
        timer = mock(ScheduledExecutorService.class);
        emitters = new ArrayDeque<>();
        broker = new ScheduleEventBroker(Runnable::run, timer, lifetime -> emitters.remove(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() { broker.shutdown(); }

    private SseEmitter subscribe(String id, String role) {
        var emitter = mock(SseEmitter.class);
        emitters.add(emitter);
        broker.subscribe(new AccountPrincipal(id, id, role), NOW.plusSeconds(3600));
        return emitter;
    }

    @Test
    void onlyTheAffectedTeacherAndAdminsReceiveChangesAndPayloadContainsNoCalendarData() throws Exception {
        var teacherA = subscribe("A", "TEACHER");
        var teacherB = subscribe("B", "TEACHER");
        var admin = subscribe("ADMIN", "ADMIN");
        broker.publish(Set.of("A"));
        verify(teacherA, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(teacherB, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(admin, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        var events = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(teacherA, times(2)).send(events.capture());
        var payloads = events.getAllValues().getLast().build().stream().map(item -> item.getData()).filter(Map.class::isInstance).toList();
        assertEquals(List.of(Map.of("revision", 1L)), payloads);
    }

    @Test
    void aFailedSocketDoesNotPreventTheOtherRecipientsFromUpdating() throws Exception {
        var broken = subscribe("A", "TEACHER");
        var healthy = subscribe("A", "TEACHER");
        doThrow(new IOException("Disconnected")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
        assertDoesNotThrow(() -> broker.publish(Set.of("A")));
        broker.publish(Set.of("A"));
        verify(broken, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void disconnectUnregistersTheSubscriber() throws Exception {
        var emitter = subscribe("A", "TEACHER");
        var complete = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(complete.capture());
        complete.getValue().run();
        broker.publish(Set.of("A"));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void subscriptionsAreBoundedByJwtExpiryAndAlwaysCloseAtTheDeadline() throws Exception {
        var emitter = mock(SseEmitter.class);
        emitters.add(emitter);
        broker.subscribe(new AccountPrincipal("A", "a", "TEACHER"), NOW.plusSeconds(20));
        var expiry = ArgumentCaptor.forClass(Runnable.class);
        verify(timer).schedule(expiry.capture(), eq(20000L), eq(TimeUnit.MILLISECONDS));
        expiry.getValue().run();
        verify(emitter).complete();
        broker.publish(Set.of("A"));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void longLivedTokensStillReconnectWithinFiveMinutes() {
        subscribe("A", "TEACHER");
        verify(timer).schedule(any(Runnable.class), eq(300000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void shutdownCompletesStreamsOnceAndRejectsNewConnections() {
        var emitter = subscribe("A", "TEACHER");
        broker.shutdown();
        broker.shutdown();
        verify(emitter, times(1)).complete();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> broker.subscribe(new AccountPrincipal("A", "a", "TEACHER"), NOW.plusSeconds(20)));
    }

    @Test
    void expiredAndUnsupportedAccountsCannotSubscribe() {
        assertThrows(BadCredentialsException.class, () -> broker.subscribe(new AccountPrincipal("A", "a", "TEACHER"), NOW));
        assertThrows(AccessDeniedException.class, () -> broker.subscribe(new AccountPrincipal("A", "a", "OTHER"), NOW.plusSeconds(20)));
    }
}
