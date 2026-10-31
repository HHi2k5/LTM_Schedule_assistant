package com.service;
import com.dto.ScheduleAdjustmentDTO.*;
import com.repository.*;
import com.model.SchedulingChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:adjustment-atomic-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000")
@AutoConfigureMockMvc @ActiveProfiles("test")
class ScheduleAdjustmentAtomicityTest extends SchedulingPlanFixture {
    @Autowired ScheduleAdjustmentService adjustments;
    @Autowired SchedulingDemandRepository demands;
    @Autowired LecturerBusySlotRepository busy;
    @SpyBean SchedulingChangeRepository journal;
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateJournalFailureRollsBackTheWholeNewDraft() throws Exception {
        var p = generate(create(department), lecturer, 6); var row = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(p.draft().summary().id()).getFirst();
        var input = new Input(p.plan().revision(), p.draft().summary().id(), adjustments.options(p.plan().id()).snapshot(), "LOCK", List.of(row.getId()), List.of(), List.of(), null, true, null, null, null, null, false, "Khóa");
        var token = adjustments.preview(p.plan().id(), input).previewToken();
        long v = versions.count(), e = entries.count(), d = demands.count(), b = busy.count(), j = journal.count();
        doThrow(new IllegalStateException("late journal failure")).when(journal).saveAndFlush(argThat((SchedulingChange c) -> c.getAction().equals("LOCK")));
        assertThrows(IllegalStateException.class, () -> adjustments.commit(p.plan().id(), new Commit(input, token)));
        assertEquals(v, versions.count()); assertEquals(e, entries.count()); assertEquals(d, demands.count()); assertEquals(b, busy.count()); assertEquals(j, journal.count());
        assertEquals(p.plan().revision(), plans.get(p.plan().id()).plan().revision()); assertEquals(p.draft().summary().id(), plans.get(p.plan().id()).draft().summary().id());
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateConfirmationJournalFailureRollsBackNewCalendarAndIdentity() throws Exception {
        var p = generate(create(department), lecturer, 6); long t = teachers.count(), j = journal.count();
        doThrow(new IllegalStateException("late confirmation journal failure")).when(journal).saveAndFlush(argThat((SchedulingChange c) -> c.getAction().equals("CONFIRM")));
        assertThrows(IllegalStateException.class, () -> plans.confirm(p.plan().id(), p.plan().revision()));
        assertTrue(projection(p.plan().id()).isEmpty()); assertEquals(t, teachers.count()); assertEquals(j, journal.count());
        assertNull(plans.get(p.plan().id()).plan().confirmedVersion()); assertEquals(p.plan().revision(), plans.get(p.plan().id()).plan().revision());
    }
}
