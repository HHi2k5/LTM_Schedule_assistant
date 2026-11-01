package com.service;

import com.security.AccountPrincipal;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Only invalidation notices are streamed; calendar data still uses the authorised week API. */
@Component
public class ScheduleEventBroker {
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final Executor outbound;
    private final ScheduledExecutorService timer;
    private final LongFunction<SseEmitter> emitters;
    private final Clock clock;
    private boolean stopping;

    public ScheduleEventBroker() {
        this(Executors.newVirtualThreadPerTaskExecutor(), newTimer(), SseEmitter::new, Clock.systemUTC());
    }

    private static ScheduledExecutorService newTimer() {
        var timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("schedule-events-heartbeat").factory());
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    ScheduleEventBroker(Executor outbound, ScheduledExecutorService timer, LongFunction<SseEmitter> emitters, Clock clock) {
        this.outbound = outbound;
        this.timer = timer;
        this.emitters = emitters;
        this.clock = clock;
        timer.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
    }

    public synchronized SseEmitter subscribe(AccountPrincipal account, Instant tokenExpiresAt) {
        if (stopping) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Máy chủ đang khởi động lại.");
        if (account == null || !("TEACHER".equals(account.role()) || "ADMIN".equals(account.role()))) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền nhận cập nhật lịch.");
        }
        long lifetime = Math.min(Duration.ofMinutes(5).toMillis(), Duration.between(clock.instant(), tokenExpiresAt).toMillis());
        if (lifetime <= 0) throw new BadCredentialsException("Phiên đăng nhập đã hết hạn.");
        var connection = new Connection(account, emitters.apply(lifetime), tokenExpiresAt);
        connections.add(connection);
        connection.emitter.onCompletion(() -> forget(connection));
        connection.emitter.onTimeout(() -> close(connection));
        connection.emitter.onError(error -> forget(connection));
        try {
            connection.emitter.send(SseEmitter.event().name("ready").data(Map.of("revision", 0)));
            connection.lease = timer.schedule(() -> close(connection), lifetime, TimeUnit.MILLISECONDS);
            if (!connection.active.get() && connection.lease != null) connection.lease.cancel(false);
        } catch (IOException | IllegalStateException | RejectedExecutionException error) {
            forget(connection);
        }
        return connection.emitter;
    }

    public void publish(Set<String> teacherIds) {
        if (teacherIds.isEmpty()) return;
        for (var connection : connections) {
            if ("ADMIN".equals(connection.account.role()) || teacherIds.contains(connection.account.teacherId())) {
                connection.revision.incrementAndGet();
                requestDelivery(connection);
            }
        }
    }

    private void heartbeat() {
        for (var connection : connections) {
            connection.ping.set(true);
            requestDelivery(connection);
        }
    }

    private void requestDelivery(Connection connection) {
        if (!connection.active.get() || !connection.writing.compareAndSet(false, true)) return;
        try {
            // Network writes never hold up an admin's committed save or another subscriber.
            outbound.execute(() -> deliver(connection));
        } catch (RejectedExecutionException error) {
            connection.writing.set(false);
            close(connection);
        }
    }

    private void deliver(Connection connection) {
        try {
            while (connection.active.get()) {
                if (!clock.instant().isBefore(connection.expiresAt)) {
                    close(connection);
                    return;
                }
                long revision = connection.revision.get();
                if (revision != connection.sentRevision) {
                    connection.emitter.send(SseEmitter.event().name("schedule-changed").data(Map.of("revision", revision)));
                    connection.sentRevision = revision;
                } else if (connection.ping.getAndSet(false)) {
                    connection.emitter.send(SseEmitter.event().name("ping").data(Map.of("revision", revision)));
                } else return;
            }
        } catch (IOException | IllegalStateException error) {
            // The servlet container owns error dispatch when a socket write fails.
            forget(connection);
        } finally {
            connection.writing.set(false);
            if (connection.active.get() && (connection.revision.get() != connection.sentRevision || connection.ping.get())) {
                requestDelivery(connection);
            }
        }
    }

    private void forget(Connection connection) {
        connection.active.set(false);
        connections.remove(connection);
        var lease = connection.lease;
        if (lease != null) lease.cancel(false);
    }

    private void close(Connection connection) {
        forget(connection);
        try {
            outbound.execute(() -> {
                try { connection.emitter.complete(); } catch (IllegalStateException ignored) { /* Already closed. */ }
            });
        } catch (RejectedExecutionException ignored) { /* Server shutdown will close the HTTP connection. */ }
    }

    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public synchronized void shutdown() {
        if (stopping) return;
        stopping = true;
        // Complete async responses before Tomcat stops, rather than waiting for bean destruction.
        connections.forEach(this::close);
        timer.shutdownNow();
        if (outbound instanceof ExecutorService executor) {
            executor.shutdown();
            // Give queued emitter completions a bounded window before Tomcat stops.
            try { executor.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    private static final class Connection {
        final AccountPrincipal account;
        final SseEmitter emitter;
        final Instant expiresAt;
        final AtomicBoolean active = new AtomicBoolean(true), writing = new AtomicBoolean(), ping = new AtomicBoolean();
        final AtomicLong revision = new AtomicLong();
        volatile long sentRevision;
        volatile ScheduledFuture<?> lease;

        Connection(AccountPrincipal account, SseEmitter emitter, Instant expiresAt) {
            this.account = account;
            this.emitter = emitter;
            this.expiresAt = expiresAt;
        }
    }
}
