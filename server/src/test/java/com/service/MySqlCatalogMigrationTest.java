package com.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@Testcontainers
class MySqlCatalogMigrationTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");

    @Test void upgradesV3PreservesLegacyDataAndCreatesScopedCatalogs() {
        var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("3").load().migrate();
        jdbc.update("INSERT INTO teachers (teacher_id, full_name, username, password, role, semester_start_date) VALUES ('GV01', 'Teacher', 'existing-account', 'unchanged-hash', 'TEACHER', '2026-08-10')");
        jdbc.update("INSERT INTO schools (id, school_code, school_name, teacher_id) VALUES (1, 'A', 'School A', 'GV01')");
        jdbc.update("INSERT INTO teaching_courses (school_id, course_code, course_name, classroom, day_of_week, start_period, num_periods, week_schedule) VALUES (1, 'NET', 'Networking', '101', 2, 1, 3, '1111')");
        jdbc.update("INSERT INTO rooms (school_code, room_code, room_type, capacity, is_lab) VALUES ('A', '101', 'THEORY_STANDARD', 60, FALSE)");

        Flyway.configure().dataSource(source).target("4").load().migrate();
        assertEquals("unchanged-hash", jdbc.queryForObject("SELECT password FROM teachers WHERE teacher_id='GV01'", String.class));
        assertEquals("1111", jdbc.queryForObject("SELECT week_schedule FROM teaching_courses WHERE school_id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM schools", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE active=TRUE AND revision=0", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM institution_lecturers", Integer.class));

        jdbc.update("INSERT INTO institutions (school_code, name) VALUES ('A', 'School A'), ('B', 'School B')");
        jdbc.update("INSERT INTO departments (id, school_code, department_code, name) VALUES (1, 'A', 'FIT', 'Faculty A'), (2, 'B', 'FIT', 'Faculty B')");
        jdbc.update("INSERT INTO institution_lecturers (id, school_code, lecturer_code, full_name, department_id, lecturer_type, account_teacher_id) VALUES (1, 'A', 'L01', 'Teacher A', 1, 'STAFF', 'GV01'), (2, 'B', 'L01', 'Teacher B', 2, 'VISITING', 'GV01')");
        jdbc.update("INSERT INTO lecturer_assignments (lecturer_id, course_code, course_name, cohort, theory_weight, practice_weight) VALUES (1, 'NET', 'Networking', '2024', 2, 8)");
        Flyway.configure().dataSource(source).target("5").load().migrate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM teaching_courses WHERE school_id=1", Integer.class));
        Flyway.configure().dataSource(source).target("7").load().migrate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM teaching_courses WHERE school_id=1 AND scheduling_plan_id IS NULL AND scheduling_entry_id IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_plans", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lecturer_busy_slots", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scheduling_plan_versions' AND column_name='visiting_change_json'", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE teaching_courses SET scheduling_plan_id=999999 WHERE school_id=1"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE teaching_courses SET scheduling_entry_id=999999 WHERE school_id=1"));
        assertEquals("Teacher A", jdbc.queryForObject("SELECT full_name FROM institution_lecturers WHERE id=1", String.class));
        assertEquals("GV01", jdbc.queryForObject("SELECT account_teacher_id FROM institution_lecturers WHERE id=1", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM institution_lecturers WHERE schedule_teacher_id IS NULL", Integer.class));
        jdbc.update("UPDATE institution_lecturers SET schedule_teacher_id='GV01' WHERE id=1");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO institution_lecturers (school_code, lecturer_code, full_name, lecturer_type, schedule_teacher_id) VALUES ('A', 'L02', 'Duplicate identity', 'STAFF', 'GV01')"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO institution_lecturers (school_code, lecturer_code, full_name, lecturer_type, schedule_teacher_id) VALUES ('A', 'L03', 'Unknown identity', 'STAFF', 'MISSING')"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM institution_lecturers", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO institution_lecturers (school_code, lecturer_code, full_name, lecturer_type) VALUES ('A', 'L01', 'Duplicate', 'STAFF')"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO lecturer_assignments (lecturer_id, course_code, course_name, cohort, theory_weight, practice_weight) VALUES (1, 'NET', 'Duplicate', '2024', 1, 1)"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO departments (school_code, department_code, name) VALUES ('UNKNOWN', 'FIT', 'Invalid institution')"));
        var invalidWeight = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "INSERT INTO lecturer_assignments (lecturer_id, course_code, course_name, cohort, theory_weight, practice_weight) VALUES (1, 'INVALID', 'Invalid', '2024', -1, 1)"));
        // MySQL reports CHECK violations as HY000/3819, which Spring JDBC leaves uncategorized.
        var sqlError = assertInstanceOf(java.sql.SQLException.class, invalidWeight.getMostSpecificCause());
        assertEquals(3819, sqlError.getErrorCode());
        jdbc.update("INSERT INTO scheduling_plans (id, school_code, department_id, name, semester_key, semester_start_date, created_at, updated_at) VALUES (900, 'A', 1, 'Old plan', 'OLD', '2026-08-10', NOW(), NOW())");
        jdbc.update("INSERT INTO scheduling_plan_versions (id, plan_id, version_number, created_at, created_by, complete, request_json, configuration_json, result_json, visiting_change_json) VALUES (900, 900, 1, NOW(), 'GV01', TRUE, '{}', '{}', '{\"old\":true}', '{}')");
        jdbc.update("INSERT INTO scheduling_demands (id, version_id, demand_index, course_code, cohort, snapshot_json) VALUES (900, 900, 1, 'NET', '2024', '{}')");
        jdbc.update("INSERT INTO scheduling_entries (id, version_id, demand_id, lecturer_id, room_id, lesson_date, start_time, end_time, start_period, num_periods, schedule_class_id, schedule_parent_id, class_group, session_type, student_count, course_name, lecturer_name, room_code) VALUES (900, 900, 900, 1, 1, '2026-08-10', '07:00:00', '09:50:00', 1, 3, 'OLD:LT', 'OLD', '01', 'THEORY', 40, 'Networking', 'Teacher A', '101')");
        Flyway.configure().dataSource(source).load().migrate();
        assertEquals("LEGACY_900", jdbc.queryForObject("SELECT entry_key FROM scheduling_entries WHERE id=900", String.class));
        assertEquals(false, jdbc.queryForObject("SELECT auto_provisioned FROM teachers WHERE teacher_id='GV01'", Boolean.class));
        assertEquals("unchanged-hash", jdbc.queryForObject("SELECT password FROM teachers WHERE teacher_id='GV01'", String.class));
        assertEquals(false, jdbc.queryForObject("SELECT locked FROM scheduling_entries WHERE id=900", Boolean.class));
        assertEquals(true, jdbc.queryForObject("SELECT customized FROM scheduling_plan_versions WHERE id=900", Boolean.class));
        assertEquals("{\"old\":true}", jdbc.queryForObject("SELECT result_json FROM scheduling_plan_versions WHERE id=900", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_changes", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO scheduling_changes (plan_id, result_version_id, action, actor, created_at, affected_entries, reason, scope_json) VALUES (900, 999999, 'LOCK', 'GV01', NOW(), 1, '', '{}')"));
        assertEquals(0, Flyway.configure().dataSource(source).load().migrate().migrationsExecuted);
    }
}
