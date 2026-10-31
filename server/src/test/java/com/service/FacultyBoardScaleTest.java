package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.Configuration;
import com.dto.FacultyBoardDTO.Query;
import com.model.InstitutionLecturer.LecturerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FacultyBoardScaleTest extends SchedulingPlanFixture {
    @Autowired FacultyBoardService board;

    @Test void representativeWeekKeepsTwentyLecturersAndFourHundredOccurrencesPaged() throws Exception {
        var lecturerIds = new ArrayList<Long>(); lecturerIds.add(lecturer.id());
        var roomIds = new ArrayList<Long>(); roomIds.add(room.id());
        for (int i = 1; i < 20; i++) {
            var next = catalogs.createLecturer(school, new LecturerInput("LOAD" + i, "Giảng viên " + i, department.id(), LecturerType.STAFF, null, true, null));
            catalogs.createAssignment(school, next.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null)); lecturerIds.add(next.id());
            roomIds.add(catalogs.createRoom(school, new RoomInput("LOAD" + i, "Phòng " + i, "THEORY", 60, null, true, null)).id());
        }
        var p = create(department); var options = plans.options(p.plan().id()); var row = WeeklyPlanExcelReaderTest.row(); row[5] = 200; row[7] = "16/08/2026"; row[9] = 6; row[10] = 0;
        p = plans.generate(p.plan().id(), p.plan().revision(), WeeklyPlanExcelReaderTest.file(new Object[][]{WeeklyPlanExcelReaderTest.HEADERS, row}),
                new Configuration(options.snapshot(), lecturerIds, roomIds, List.of(), options.periodChoices().getFirst().id(), false));
        assertTrue(p.draft().summary().complete()); assertEquals(400, p.draft().entryCount());
        entityManager.flush(); entityManager.clear();
        long started = System.nanoTime();
        var calendar = board.overview(p.plan().id(), p.draft().summary().id(), Query.defaults());
        long calendarMs = (System.nanoTime() - started) / 1_000_000;
        assertEquals(20, calendar.totalRows()); assertEquals(10, calendar.groups().size()); assertEquals(2, calendar.pageCount());
        assertEquals(400, calendar.stats().sessions()); assertEquals(1200, calendar.stats().weekPeriods());
        assertTrue(calendar.groups().stream().mapToInt(g -> g.entries().size()).sum() < 400);
        entityManager.clear(); started = System.nanoTime();
        var export = board.overview(p.plan().id(), p.draft().summary().id(), new Query(1, "export", null, 0, null, null, null, null, null, null, null, null, null));
        long exportMs = (System.nanoTime() - started) / 1_000_000;
        assertEquals(400, export.entries().size()); assertEquals(200, export.assignments().size());
        assertEquals(0, export.stats().conflictingEntries());
        System.out.printf("Faculty board sample: 20 lecturers, 200 classes, 400 occurrences; H2 calendar=%dms, export-data=%dms%n", calendarMs, exportMs);
    }
}
