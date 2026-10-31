package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import com.security.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true") @Testcontainers
class MySqlCatalogSchedulingTest {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired CatalogService catalogs;
    @Autowired CatalogSchedulingService scheduler;
    @Autowired TeacherRepository teachers;
    @Autowired RoomRepository rooms;

    @Test void mysqlPersistsStableIdentityAndRollsBackTheWholeIncompleteRun() throws Exception {
        String code = "MYSQL_CATALOG";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal("ADMIN", "manager", "ADMIN"), null, List.of()));
        try {
            catalogs.createInstitution(new InstitutionInput(code, "MySQL catalog", true, null));
            var lecturer = catalogs.createLecturer(code, new LecturerInput("STAFF01", "GV", null, LecturerType.STAFF, null, true, null));
            catalogs.createAssignment(code, lecturer.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
            var room = catalogs.createRoom(code, new RoomInput("101", "Phòng", "THEORY", 60, null, true, null));
            var options = scheduler.options(code);
            var response = scheduler.generate(code, CatalogSchedulingTest.START, CatalogSchedulingTest.plan(6, 0),
                    new Configuration(options.snapshot(), List.of(lecturer.id()), List.of(room.id()), List.of(), options.periodChoices().getFirst().id(), true));
            assertTrue(response.isSaved());
            String identity = response.getCatalogContext().lecturers().getFirst().teacherId();
            assertEquals(identity, catalogs.listLecturers(code).getFirst().scheduleTeacherId());
            assertEquals(lecturer.lecturerCode(), teachers.findById(identity).orElseThrow().getUsername());
            var next = catalogs.createLecturer(code, new LecturerInput("STAFF02", "GV mới", null, LecturerType.STAFF, null, true, null));
            catalogs.createAssignment(code, next.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
            var small = catalogs.createRoom(code, new RoomInput("SMALL", "Quá nhỏ", "THEORY", 10, null, true, null));
            options = scheduler.options(code); long count = teachers.count();
            response = scheduler.generate(code, CatalogSchedulingTest.START, CatalogSchedulingTest.plan(6, 0),
                    new Configuration(options.snapshot(), List.of(next.id()), List.of(small.id()), List.of(), options.periodChoices().getFirst().id(), true));
            assertFalse(response.isSaved()); assertEquals(count, teachers.count());
            assertNull(catalogs.listLecturers(code).stream().filter(l -> l.id().equals(next.id())).findFirst().orElseThrow().scheduleTeacherId());
            assertFalse(teachers.findTeacherWithFullSchedule(identity).orElseThrow().getSchools().getFirst().getCourses().isEmpty());
            assertEquals(2, rooms.findBySchoolCodeOrderByCapacityAsc(code).size());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
