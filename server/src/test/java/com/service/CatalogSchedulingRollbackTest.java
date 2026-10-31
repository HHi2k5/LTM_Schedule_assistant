package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import com.security.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class CatalogSchedulingRollbackTest {
    @Autowired CatalogService catalogs;
    @Autowired CatalogSchedulingService scheduler;
    @Autowired InstitutionLecturerRepository lecturers;
    @Autowired LecturerAssignmentRepository assignments;
    @Autowired RoomRepository rooms;
    @Autowired InstitutionRepository schools;
    @Autowired TeacherRepository teachers;

    @Test void insufficientRoomCapacityRollsBackNewNonLoginProfileAndRosterMapping() throws Exception {
        String code = "CS_ROLLBACK";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal("ADMIN", "manager", "ADMIN"), null, List.of()));
        catalogs.createInstitution(new InstitutionInput(code, "Rollback", true, null));
        var lecturer = catalogs.createLecturer(code, new LecturerInput("UNLINKED", "GV mới", null, LecturerType.STAFF, null, true, null));
        var assignment = catalogs.createAssignment(code, lecturer.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null));
        var room = catalogs.createRoom(code, new RoomInput("SMALL", "Quá nhỏ", "THEORY", 10, null, true, null));
        long before = teachers.count();
        try {
            var options = scheduler.options(code);
            var response = scheduler.generate(code, CatalogSchedulingTest.START, CatalogSchedulingTest.plan(6, 0),
                    new Configuration(options.snapshot(), List.of(lecturer.id()), List.of(room.id()), List.of(), options.periodChoices().getFirst().id(), true));
            assertFalse(response.isSaved()); assertEquals(before, teachers.count());
            assertNull(catalogs.listLecturers(code).getFirst().scheduleTeacherId());
            assertEquals(assignment.id(), catalogs.listAssignments(code, lecturer.id()).getFirst().id());
            assertEquals(options.snapshot(), scheduler.options(code).snapshot());
        } finally {
            assignments.deleteById(assignment.id()); lecturers.deleteById(lecturer.id()); rooms.deleteById(room.id()); schools.deleteById(code);
            SecurityContextHolder.clearContext();
        }
    }
}
