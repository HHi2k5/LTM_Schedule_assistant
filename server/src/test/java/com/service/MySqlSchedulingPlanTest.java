package com.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import com.dto.SchedulingPlanDTO.Detail;
import com.exception.SchedulingPlanConflictException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true") @Testcontainers
class MySqlSchedulingPlanTest extends SchedulingPlanContract {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFacultyConfirmationsSerializeAndTheSecondSeesFreshRoomReservations() throws Exception {
        var a = generate(create(department), lecturer, 6);
        var b = generate(create(otherDepartment), otherLecturer, 6);
        long count = teachers.count(); var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> confirmAfter(start, a));
            var second = workers.submit(() -> confirmAfter(start, b));
            start.countDown();
            assertEquals(Set.of("confirmed", "conflict"), new HashSet<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))));
        }
        assertEquals(count, teachers.count());
        assertEquals(1, List.of(plans.get(a.plan().id()), plans.get(b.plan().id())).stream().filter(p -> "CONFIRMED".equals(p.plan().state())).count());
        assertTrue(projection(a.plan().id()).isEmpty() ^ projection(b.plan().id()).isEmpty());
    }

    private String confirmAfter(CountDownLatch start, Detail draft) throws Exception {
        start.await(); authenticate(adminId, "ADMIN");
        try { plans.confirm(draft.plan().id(), draft.plan().revision()); return "confirmed"; }
        catch (SchedulingPlanConflictException e) { assertTrue(e.getConflicts().stream().anyMatch(c -> c.type().equals("ROOM_CONFLICT"))); return "conflict"; }
        finally { SecurityContextHolder.clearContext(); }
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDraftWritersKeepOneVersionWithoutOrphanOccurrences() throws Exception {
        var created = create(department); var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                start.await(); authenticate(adminId, "ADMIN");
                try { generate(created, lecturer, 6); return true; }
                catch (org.springframework.dao.ConcurrencyFailureException | org.springframework.dao.DataIntegrityViolationException e) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var first = workers.submit(task); var second = workers.submit(task); start.countDown();
            assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
        var reopened = plans.get(created.plan().id()); assertEquals(1, reopened.versions().size());
        assertEquals(reopened.draft().entryCount(), plans.entryPage(created.plan().id(), reopened.draft().summary().id(), 0).total());
        assertTrue(projection(created.plan().id()).isEmpty());
    }
}
