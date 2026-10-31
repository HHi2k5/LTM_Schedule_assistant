package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.SchedulingPlanDTO.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest @ActiveProfiles("test")
class SchedulingPlanAtomicityTest {
    @Autowired SchedulingPlanService plans;
    @Autowired CatalogService catalogs;
    @Autowired TeacherRepository teachers;
    @SpyBean SchedulingPlanRepository store;

    @Test void lateFailureRollsBackPublicationPersonalCalendarAndNewIdentityTogether() throws Exception {
        String school = "ROLL_" + UUID.randomUUID().toString().substring(0, 8);
        SchedulingPlanContract.authenticate("ADMIN", "ADMIN");
        try {
            catalogs.createInstitution(new InstitutionInput(school, "Rollback", true, null));
            var dept = catalogs.createDepartment(school, new DepartmentInput("FIT", "Khoa", true, null));
            var lecturer = catalogs.createLecturer(school, new LecturerInput("GV01", "GV", dept.id(), LecturerType.STAFF, null, true, null));
            catalogs.createAssignment(school, lecturer.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
            var room = catalogs.createRoom(school, new RoomInput("101", "Phòng", "THEORY", 60, null, true, null));
            var created = plans.create(new Create(school, dept.id(), "Bản nháp", "HK1", CatalogSchedulingTest.START));
            var draft = plans.generate(created.plan().id(), created.plan().revision(), CatalogSchedulingTest.plan(6, 0), SchedulingPlanContract.config(plans.options(created.plan().id()), lecturer.id(), room.id()));
            long count = teachers.count();
            // Fail only after the projection has been flushed and the version updated.
            doThrow(new IllegalStateException("late publication failure")).when(store).saveAndFlush(argThat(p -> p.getId().equals(created.plan().id())));
            assertThrows(IllegalStateException.class, () -> plans.confirm(draft.plan().id(), draft.plan().revision()));
            var reopened = plans.get(created.plan().id());
            assertEquals(draft.plan().revision(), reopened.plan().revision()); assertNull(reopened.plan().confirmedVersion());
            assertNull(reopened.draft().summary().confirmedAt()); assertFalse(reopened.draft().result().isSaved());
            assertEquals(count, teachers.count()); assertNull(catalogs.listLecturers(school).getFirst().scheduleTeacherId());
            assertTrue(teachers.findAllWithFullSchedule().stream().flatMap(t -> t.getSchools().stream()).noneMatch(s -> school.equalsIgnoreCase(s.getSchoolCode())));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
