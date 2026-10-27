package com.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@Testcontainers
class MySqlScheduleTest extends SchedulePersistenceContract {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @org.junit.jupiter.api.Test void migrationsCanUpgradeLegacyAccountColumnsWithoutLosingScheduleData() {
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .baselineOnMigrate(true).baselineVersion("0").load();
        jdbc.update("INSERT INTO teachers (teacher_id, full_name, semester_start_date) VALUES ('LEGACY', 'Teacher', '2026-08-10')");
        jdbc.execute("ALTER TABLE teachers DROP COLUMN username, DROP COLUMN password, DROP COLUMN role");
        jdbc.execute("ALTER TABLE rooms DROP INDEX uq_room_school_code");
        jdbc.execute("ALTER TABLE rooms ADD CONSTRAINT old_room_code_unique UNIQUE (room_code)");
        jdbc.execute("DROP TABLE flyway_schema_history");
        flyway.migrate();
        org.junit.jupiter.api.Assertions.assertEquals("Teacher", jdbc.queryForObject("SELECT full_name FROM teachers WHERE teacher_id='LEGACY'", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("TEACHER", jdbc.queryForObject("SELECT role FROM teachers WHERE teacher_id='LEGACY'", String.class));
    }
}
