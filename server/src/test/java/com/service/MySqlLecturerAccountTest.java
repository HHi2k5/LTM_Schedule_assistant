package com.service;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true") @Testcontainers
class MySqlLecturerAccountTest extends LecturerAccountContract {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver"); r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @org.junit.jupiter.api.Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentSameUsernameAcrossSchoolsCreatesExactlyOneAccount() throws Exception {
        String other = school + "_SECOND";
        catalogs.createInstitution(new com.dto.CatalogDTO.InstitutionInput(other, "Trường khác", true, null));
        var start = new java.util.concurrent.CountDownLatch(1); long count = teachers.count();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.function.Function<String, java.util.concurrent.Callable<Boolean>> action = target -> () -> {
                start.await(); authenticate(adminId, "ADMIN");
                try { catalogs.createLecturer(target, new com.dto.CatalogDTO.LecturerInput(code(), "Đặng Thị Mỹ Lệ", null, com.model.InstitutionLecturer.LecturerType.STAFF, null, true, null)); return true; }
                catch (IllegalArgumentException e) { return false; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            };
            var a = pool.submit(action.apply(school)); var b = pool.submit(action.apply(other)); start.countDown();
            org.junit.jupiter.api.Assertions.assertNotEquals(a.get(30, java.util.concurrent.TimeUnit.SECONDS), b.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        org.junit.jupiter.api.Assertions.assertEquals(count + 1, teachers.count());
    }
}
