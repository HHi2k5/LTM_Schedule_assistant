package com.service;

import com.dto.*;
import com.repository.*;
import com.security.AccountPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
abstract class SchedulePersistenceContract {
    @Autowired ScheduleService schedules;
    @Autowired AutoScheduleService automatic;
    @Autowired RoomRepository rooms;
    @Autowired TeacherRepository teachers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ScheduleWriteLock writeLock;
    @Autowired ExcelScheduleService excel;
    @Autowired AuthService auth;

    static void admin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal("QL01", "manager", "ADMIN"), null, List.of()));
    }
    @BeforeEach void setup() {
        for (String table : List.of("teaching_courses", "school_periods", "schools", "teachers", "rooms")) jdbc.update("DELETE FROM " + table);
        admin();
        new TransactionTemplate(manager).executeWithoutResult(status -> writeLock.acquire());
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void realPersistenceRejectsEmptyAndAnchorChangesWithoutChangingOldLessons() {
        var input = BackendRegressionTest.upload(); schedules.saveOrUpdateSchoolSchedule(input);
        var empty = BackendRegressionTest.upload(); empty.setCourses(List.of());
        assertThrows(IllegalArgumentException.class, () -> schedules.saveOrUpdateSchoolSchedule(empty));
        var changed = BackendRegressionTest.upload(); changed.setSemesterStartDate(BackendRegressionTest.MONDAY.plusWeeks(1));
        assertThrows(IllegalArgumentException.class, () -> schedules.saveOrUpdateSchoolSchedule(changed));
        var result = schedules.getWeeklySchedule("GV01", 1);
        assertEquals(1, result.getLessons().size()); assertEquals(BackendRegressionTest.MONDAY, result.getLessons().getFirst().getDate());
        assertEquals(input.getSchool().getSchoolId(), result.getSchools().getFirst().getSchoolId());
        assertEquals(input.getSchool().getPeriods(), result.getSchools().getFirst().getPeriods());
    }
    @Test void partialPlanKeepsExistingDatabaseSchedule() {
        var input = BackendRegressionTest.upload(); input.getSchool().setSchoolId("A"); schedules.saveOrUpdateSchoolSchedule(input);
        var result = automatic.generateScheduleWithRooms(BackendRegressionTest.request(), List.of());
        assertFalse(result.isSaved());
        assertEquals(1, schedules.getWeeklySchedule("GV01", 1).getLessons().size());
    }
    @Test void outerFailureRollsBackRealScheduleWrites() {
        var tx = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            schedules.saveOrUpdateSchoolSchedule(BackendRegressionTest.upload());
            throw new IllegalStateException("failure after schedule save");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM teachers", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM schools", Integer.class));
    }
    @Test void concurrentRunsCannotBookTheSameRoomAtTheSameTime() throws Exception {
        rooms.saveAndFlush(BackendRegressionTest.room("A"));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<AutoScheduleResponse>>();
            for (String id : List.of("GV01", "GV02")) futures.add(pool.submit(() -> {
                admin(); start.await();
                try {
                    var request = BackendRegressionTest.request(); request.getCourses().getFirst().setTeachers(List.of(BackendRegressionTest.candidate(id, null)));
                    return automatic.generateScheduleWithRooms(request, rooms.findBySchoolCodeOrderByCapacityAsc("A"));
                } finally { SecurityContextHolder.clearContext(); }
            }));
            start.countDown();
            var a = futures.get(0).get(20, TimeUnit.SECONDS); var b = futures.get(1).get(20, TimeUnit.SECONDS);
            assertTrue(a.isSaved()); assertTrue(b.isSaved());
            var slotA = a.getScheduledClasses().getFirst(); var slotB = b.getScheduledClasses().getFirst();
            assertFalse(slotA.getDayOfWeek() == slotB.getDayOfWeek() && slotA.getStartPeriod() == slotB.getStartPeriod());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM teaching_courses", Integer.class));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }
    @Test void roomCodesCanRepeatAcrossSchoolsButNotWithinOneSchool() {
        rooms.saveAndFlush(BackendRegressionTest.room("A")); rooms.saveAndFlush(BackendRegressionTest.room("B"));
        assertEquals(2, rooms.count());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> rooms.saveAndFlush(BackendRegressionTest.room("A")));
    }

    @Test void checkedExcelReadFailureAfterRoomInsertRollsBackEverything() throws Exception {
        var plan = WeeklyPlanExcelReaderTest.plan();
        byte[] bytes = excelFile(new Object[][]{{"Mã phòng", "Loại phòng", "Sức chứa", "Tòa nhà"}, {"101", "THEORY", 60, "A2"}}).getBytes();
        var sawWrite = new java.util.concurrent.atomic.AtomicBoolean();
        var broken = new org.springframework.mock.web.MockMultipartFile("roomFile", "rooms.xlsx", "application/octet-stream", bytes) {
            @Override public java.io.InputStream getInputStream() {
                return new java.io.FilterInputStream(new java.io.ByteArrayInputStream(bytes)) {
                    @Override public void close() throws java.io.IOException {
                        super.close();
                        if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) > 0) {
                            sawWrite.set(true);
                            throw new java.io.IOException("close failure after room insert");
                        }
                    }
                };
            }
        };
        assertThrows(java.io.IOException.class, () -> excel.autoScheduleFrom3Files(plan, WeeklyPlanExcelReaderTest.teachers(), broken, "A", "A", BackendRegressionTest.MONDAY));
        assertTrue(sawWrite.get(), "the failure must happen after a real room INSERT");
        assertEquals(0, rooms.count(), "room INSERT must roll back");
        assertEquals(0, teachers.count(), "no account from another test may remain");
    }

    @Test void concurrentRegistrationCannotCreateDuplicateUsernames() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int n = 0; n < 2; n++) futures.add(pool.submit(() -> {
                start.await(); var request = new AuthRequest(); request.setUsername("same-user"); request.setPassword("password123");
                try { auth.register(request); return true; }
                catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) { return false; }
            }));
            start.countDown();
            int success = 0; for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) success++;
            assertEquals(1, success); assertEquals(1, teachers.count());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test void newThreeFileWorkflowPersistsEveryGroupsFullHoursAndReloadsActualWeeklyLessons() throws Exception {
        var roomFile = excelFile(new Object[][]{{"Mã phòng", "Loại phòng", "Sức chứa", "Tòa nhà"},
                {"101", "THEORY", 60, "A2"}, {"LAB", "PRACTICE", 30, "A2"}});
        var result = excel.autoScheduleFrom3Files(WeeklyPlanExcelReaderTest.plan(), WeeklyPlanExcelReaderTest.teachers(),
                roomFile, "A", "School", BackendRegressionTest.MONDAY);
        assertTrue(result.isSaved(), result.getUnscheduledWarnings().toString());
        assertEquals(3, result.getTotalClassesScheduled());
        assertEquals(120, result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getScheduledPeriods).sum());
        int periods = 0;
        for (String teacher : List.of("A", "B")) for (int week = 1; week <= 16; week++) {
            for (var lesson : schedules.getWeeklySchedule(teacher, week).getLessons()) {
                assertFalse(lesson.isConflict()); periods += lesson.getEndPeriod() - lesson.getStartPeriod() + 1;
            }
        }
        assertEquals(120, periods);
    }

    @Test void changingLabCapacityReplacesOldSplitGroupsWithOneWholeClass() {
        var request = WeeklyScheduleEngineTest.request(0, 30);
        var small = WeeklyScheduleEngineTest.rooms().get(1);
        assertTrue(automatic.generateScheduleWithRooms(request, List.of(small)).isSaved());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT schedule_class_id) FROM teaching_courses WHERE source_type='WEEKLY_AUTO'", Integer.class));
        var large = com.model.Room.builder().roomCode("LAB50").schoolCode("A").isLab(true).capacity(50).roomType("LAB_PRACTICE").build();
        var result = automatic.generateScheduleWithRooms(request, List.of(large));
        assertTrue(result.isSaved()); assertEquals(1, result.getClassWorkloads().size());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getStudentCount() == 50));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT schedule_class_id) FROM teaching_courses WHERE source_type='WEEKLY_AUTO'", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT SUM(num_periods * (LENGTH(week_schedule) - LENGTH(REPLACE(week_schedule, '1', '')))) FROM teaching_courses WHERE source_type='WEEKLY_AUTO'", Integer.class));
    }

    @Test void weeklyReplacementClearsWithdrawnAssignmentsAndPreservesPersonalRowsAndOtherSchools() {
        var personal = BackendRegressionTest.upload(); personal.setTeacherId("A"); personal.getSchool().setSchoolId("A");
        schedules.saveOrUpdateSchoolSchedule(personal);
        var other = BackendRegressionTest.upload(); other.setTeacherId("A"); other.getSchool().setSchoolId("OTHER");
        schedules.saveOrUpdateSchoolSchedule(other);
        var request = WeeklyScheduleEngineTest.request(30, 0);
        request.getCourses().getFirst().setTeachers(List.of(WeeklyScheduleEngineTest.teacher("A", 1, 0)));
        var first = automatic.generateScheduleWithRooms(request, WeeklyScheduleEngineTest.rooms()); assertTrue(first.isSaved());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM teaching_courses WHERE source_type='WEEKLY_AUTO'", Integer.class) > 0);
        assertTrue(jdbc.queryForObject("SELECT plan_snapshot FROM teaching_courses WHERE source_type='WEEKLY_AUTO' LIMIT 1", String.class).contains("\"theoryPeriods\":30"));
        int lessons = 0;
        for (int week = 1; week <= 16; week++) lessons += schedules.getWeeklySchedule("A", week).getLessons().size();
        assertEquals(first.getTotalLessonsScheduled() + 2, lessons);
        request.getCourses().getFirst().setTeachers(List.of(WeeklyScheduleEngineTest.teacher("B", 1, 0)));
        assertTrue(automatic.generateScheduleWithRooms(request, WeeklyScheduleEngineTest.rooms()).isSaved());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM teaching_courses c JOIN schools s ON c.school_id=s.id WHERE s.teacher_id='A' AND c.source_type='WEEKLY_AUTO'", Integer.class));
        assertEquals(2, schedules.getWeeklySchedule("A", 1).getLessons().size());
        assertEquals(30, jdbc.queryForObject("SELECT SUM(c.num_periods * (LENGTH(c.week_schedule) - LENGTH(REPLACE(c.week_schedule, '1', '')))) FROM teaching_courses c WHERE c.source_type='WEEKLY_AUTO'", Integer.class));
        var impossible = WeeklyScheduleEngineTest.request(30, 30);
        assertFalse(automatic.generateScheduleWithRooms(impossible, List.of(BackendRegressionTest.room("A"))).isSaved());
        assertEquals(30, jdbc.queryForObject("SELECT SUM(c.num_periods * (LENGTH(c.week_schedule) - LENGTH(REPLACE(c.week_schedule, '1', '')))) FROM teaching_courses c WHERE c.source_type='WEEKLY_AUTO'", Integer.class));
    }

    static org.springframework.mock.web.MockMultipartFile excelFile(Object[][] rows) throws Exception {
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.createSheet("Data");
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c].toString());
            }
            book.write(out);
            return new org.springframework.mock.web.MockMultipartFile("file", "data.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
}
