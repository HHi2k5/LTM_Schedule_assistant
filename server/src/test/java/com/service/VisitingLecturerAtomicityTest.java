package com.service;

import com.dto.CatalogDTO.*;
import com.dto.VisitingLecturerDTO.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:visiting-atomic-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000")
@AutoConfigureMockMvc @ActiveProfiles("test")
class VisitingLecturerAtomicityTest extends SchedulingPlanFixture {
    @Autowired VisitingLecturerService visiting;
    @Autowired InstitutionLecturerRepository roster;
    @Autowired LecturerBusySlotRepository busySlots;
    @SpyBean SchedulingPlanRepository store;

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidSecondAssignmentRollsBackTheWholeNewProfile() throws Exception {
        var p = create(department); long count = roster.count(), accountCount = teachers.count();
        var a = new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> visiting.createProfile(p.plan().id(), new Profile(p.plan().revision(), "ATOMIC", "GV", null, List.of(a, a))));
        assertEquals(count, roster.count()); assertEquals(accountCount, teachers.count());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateDraftFailureRollsBackNewVersionDemandsEntriesAndBusyRows() throws Exception {
        var p = generate(create(department), lecturer, 6);
        var visitor = catalogs.createLecturer(school, new LecturerInput("VIS_" + school, "GV", department.id(), LecturerType.VISITING, null, true, null));
        catalogs.createAssignment(school, visitor.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null));
        var source = visiting.options(p.plan().id());
        var input = new Input(p.plan().revision(), p.draft().summary().id(), source.snapshot(), visitor.id(), "RECEIVE", "KEEP", List.of(source.classes().getFirst().classId()), null, List.of(),
                List.of(new Availability(CatalogSchedulingTest.START, CatalogSchedulingTest.START, null, LocalTime.of(23, 0), LocalTime.of(23, 30), "Bận")));
        var preview = visiting.preview(p.plan().id(), input); long versionsBefore = versions.count(), entriesBefore = entries.count(), busyBefore = busySlots.count();
        doThrow(new IllegalStateException("late draft failure")).when(store).saveAndFlush(argThat(value -> value.getId().equals(p.plan().id())));
        assertThrows(IllegalStateException.class, () -> visiting.commit(p.plan().id(), new Commit(input, preview.previewToken())));
        assertEquals(versionsBefore, versions.count()); assertEquals(entriesBefore, entries.count()); assertEquals(busyBefore, busySlots.count());
        assertEquals(p.draft().summary().id(), plans.get(p.plan().id()).draft().summary().id());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateConfirmationFailureAlsoRollsBackBusyPublicationAndNewCalendarIdentity() throws Exception {
        var p = generate(create(department), lecturer, 6); p = plans.confirm(p.plan().id(), p.plan().revision());
        var visitor = catalogs.createLecturer(school, new LecturerInput("VIS_" + school, "GV", department.id(), LecturerType.VISITING, null, true, null));
        catalogs.createAssignment(school, visitor.id(), new AssignmentInput("NET", "Networking", "2024", 1, 1, true, null));
        var source = visiting.options(p.plan().id()); var input = new Input(p.plan().revision(), p.draft().summary().id(), source.snapshot(), visitor.id(), "RECEIVE", "KEEP", List.of(source.classes().getFirst().classId()), null, List.of(),
                List.of(new Availability(CatalogSchedulingTest.START, CatalogSchedulingTest.START, null, LocalTime.of(23, 0), LocalTime.of(23, 30), "Bận")));
        var preview = visiting.preview(p.plan().id(), input); var edited = visiting.commit(p.plan().id(), new Commit(input, preview.previewToken()));
        long teachersBefore = teachers.count(); var oldProjection = projection(p.plan().id()).stream().map(com.model.TeachingCourse::getId).sorted().toList();
        doThrow(new IllegalStateException("late confirmation failure")).when(store).saveAndFlush(argThat(value -> value.getId().equals(edited.plan().id())));
        assertThrows(IllegalStateException.class, () -> plans.confirm(edited.plan().id(), edited.plan().revision()));
        assertEquals(teachersBefore, teachers.count()); assertNull(roster.findById(visitor.id()).orElseThrow().getScheduleTeacher());
        assertTrue(busySlots.findByVersion_Id(edited.draft().summary().id()).stream().allMatch(b -> b.getScheduleTeacher() == null));
        assertEquals(oldProjection, projection(p.plan().id()).stream().map(com.model.TeachingCourse::getId).sorted().toList());
        assertEquals(1, plans.get(p.plan().id()).plan().confirmedVersion());
    }
}
