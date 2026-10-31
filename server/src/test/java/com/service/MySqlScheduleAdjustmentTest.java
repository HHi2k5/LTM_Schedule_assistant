package com.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.dto.ScheduleAdjustmentDTO.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true") @Testcontainers
class MySqlScheduleAdjustmentTest extends ScheduleAdjustmentContract {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver"); r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousAdjustmentsKeepExactlyOneNewVersionAndOneJournalRecord() throws Exception {
        var p = generate(create(department), lecturer, 6);
        var input = command(p, "LOCK", List.of(rows(p).getFirst().getId()), List.of(), List.of(), null, true, null, false);
        var token = adjustments.preview(p.plan().id(), input).previewToken(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                start.await(); authenticate(adminId, "ADMIN");
                try { adjustments.commit(p.plan().id(), new Commit(input, token)); return true; }
                catch (org.springframework.dao.ConcurrencyFailureException e) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a = pool.submit(action); var b = pool.submit(action); start.countDown(); assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
        assertEquals(2, plans.get(p.plan().id()).versions().size()); assertEquals(2, plans.changeLog(p.plan().id(), 0).total());
        assertEquals(1, rows(plans.get(p.plan().id())).stream().filter(com.model.SchedulingEntry::isLocked).count());
    }
}
