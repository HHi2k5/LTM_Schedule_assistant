package com.service;

import com.dto.*;
import com.model.*;
import com.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyScheduleEngineTest {
    final TeacherRepository teachers = mock(TeacherRepository.class);
    final ScheduleService saves = mock(ScheduleService.class);
    final AutoScheduleService automatic = new AutoScheduleService(teachers, mock(RoomRepository.class), saves, mock(ScheduleWriteLock.class));

    static TeacherCandidateDTO teacher(String id, int lt, int th) {
        return TeacherCandidateDTO.builder().teacherId(id).teacherName(id).theoryWeight(lt).practiceWeight(th).build();
    }
    static SchoolWideScheduleRequest request(int theory, int practice) {
        var request = BackendRegressionTest.request();
        var c = request.getCourses().getFirst();
        c.setStt("2"); c.setCredits(3); c.setCohort("2024");
        c.setStudyStartDate(BackendRegressionTest.MONDAY); c.setStudyEndDate(BackendRegressionTest.MONDAY.plusWeeks(16).minusDays(1));
        c.setTheoryPeriods(theory); c.setPracticePeriods(practice);
        c.setTeachers(List.of(teacher("A", 2, 8), teacher("B", 8, 2)));
        return request;
    }
    static List<Room> rooms() {
        return List.of(BackendRegressionTest.room("A"), Room.builder().roomCode("LAB").schoolCode("A").isLab(true).capacity(30).roomType("LAB_PRACTICE").build());
    }
    AutoScheduleResponse generate(SchoolWideScheduleRequest request) { return automatic.generateScheduleWithRooms(request, rooms()); }
    static int hours(GeneratedClassSlot s) { return (int) s.getWeekSchedule().chars().filter(c -> c != '-' && c != ' ').count() * s.getNumPeriods(); }

    @ParameterizedTest
    @CsvSource({"50,50,1,50", "50,30,2,25", "51,50,2,26", "50,20,3,17", "100,50,2,50"})
    void practiceGroupsFollowActualLabCapacity(int students, int capacity, int expectedGroups, int largestGroup) {
        var request = request(0, 30); var course = request.getCourses().getFirst(); course.setClassSize(students);
        assertNull(course.getPracticeGroupSize());
        var lab = Room.builder().roomCode("LAB").schoolCode("A").isLab(true).capacity(capacity).roomType("LAB_PRACTICE").build();
        var result = automatic.generateScheduleWithRooms(request, List.of(lab));
        assertTrue(result.isSaved(), result.getUnscheduledWarnings().toString());
        var sizes = new HashMap<String, Integer>();
        for (var slot : result.getScheduledClasses()) {
            sizes.put(slot.getClassId(), slot.getStudentCount());
            assertEquals("PRACTICE", slot.getSessionType()); assertTrue(slot.getStudentCount() <= slot.getRoomCapacity());
        }
        assertEquals(expectedGroups, sizes.size()); assertEquals(expectedGroups, result.getTotalClassesScheduled());
        assertEquals(students, sizes.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(largestGroup, sizes.values().stream().mapToInt(Integer::intValue).max().orElseThrow());
        assertEquals(expectedGroups * 30, result.getScheduledClasses().stream().mapToInt(WeeklyScheduleEngineTest::hours).sum());
        assertNoConflicts(result);
    }

    @Test void fullClassUsesTheLargeLabEvenWhenSmallerLabsAndLargerTheoryRoomsExist() {
        var small = rooms().get(1);
        var large = Room.builder().roomCode("LAB50").schoolCode("A").isLab(true).capacity(50).roomType("LAB_PRACTICE").build();
        var theory = Room.builder().roomCode("LT200").schoolCode("A").capacity(200).roomType("THEORY_LARGE").build();
        var foreign = Room.builder().roomCode("FOREIGN").schoolCode("OTHER").isLab(true).capacity(200).roomType("LAB_PRACTICE").build();
        var result = automatic.generateScheduleWithRooms(request(0, 30), List.of(small, large, theory, foreign));
        assertTrue(result.isSaved()); assertEquals(1, result.getClassWorkloads().size());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getStudentCount() == 50 && s.getRoom().equals("LAB50")));
    }

    @Test void explicitSyllabusGroupLimitStillAppliesInALargeLab() {
        var request = request(0, 30); request.getCourses().getFirst().setPracticeGroupSize(20);
        var large = Room.builder().roomCode("LAB50").schoolCode("A").isLab(true).capacity(50).roomType("LAB_PRACTICE").build();
        var result = automatic.generateScheduleWithRooms(request, List.of(large));
        assertTrue(result.isSaved()); assertEquals(3, result.getClassWorkloads().size());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getStudentCount() <= 20));
        assertEquals(90, result.getScheduledClasses().stream().mapToInt(WeeklyScheduleEngineTest::hours).sum());
    }

    @Test void missingLabsReportMissingCapacityWithoutInventingThirtyStudentGroups() {
        var result = automatic.generateScheduleWithRooms(request(0, 30), List.of(BackendRegressionTest.room("A")));
        assertFalse(result.isSaved()); verifyNoInteractions(saves);
        assertEquals(1, result.getClassWorkloads().size()); assertEquals(30, result.getClassWorkloads().getFirst().getMissingPeriods());
        assertTrue(result.getUnscheduledWarnings().getFirst().contains("LAB đủ 50 chỗ"));
    }

    @Test void varyingWeeklyLengthsCompleteThirtyPeriodsAndPersistTheSnapshot() {
        var request = request(30, 0); var c = request.getCourses().getFirst();
        c.setStudyEndDate(c.getStudyStartDate().plusWeeks(12).minusDays(1));
        var result = generate(request);
        assertTrue(result.isSaved()); assertEquals(1, result.getTotalClassesScheduled());
        assertEquals(30, result.getScheduledClasses().stream().mapToInt(WeeklyScheduleEngineTest::hours).sum());
        assertEquals(Set.of(2, 3), new HashSet<>(result.getScheduledClasses().stream().map(GeneratedClassSlot::getNumPeriods).toList()));
        assertEquals(12, result.getTotalLessonsScheduled());
        org.mockito.ArgumentCaptor<Map<String, List<TeachingCourseDTO>>> capture = org.mockito.ArgumentCaptor.captor();
        verify(saves).replaceWeeklyGenerated(eq(request), capture.capture(), anyMap());
        var rows = capture.getValue().values().stream().flatMap(List::stream).toList();
        assertTrue(rows.stream().allMatch(r -> r.getScheduleClassId() != null && "THEORY".equals(r.getSessionType())
                && r.getPlanSnapshot().contains("\"theoryPeriods\":30") && r.getPlanSnapshot().contains("\"theoryWeight\":8")));
    }

    @Test void independentTeachersAndEveryPracticeGroupReceiveTheFullSyllabusHours() {
        for (int practice : List.of(30, 45)) {
            var request = request(30, practice);
            request.getCourses().getFirst().setTeachers(List.of(teacher("A", 0, 8), teacher("B", 8, 0)));
            var result = generate(request);
            assertTrue(result.isSaved()); assertEquals(3, result.getClassWorkloads().size());
            assertEquals(3, result.getTotalClassesScheduled());
            for (var w : result.getClassWorkloads()) {
                assertEquals(w.getRequiredPeriods(), w.getScheduledPeriods()); assertEquals(0, w.getMissingPeriods());
                assertEquals(w.getSessionType().equals("THEORY") ? "B" : "A", w.getTeacherId());
            }
            assertEquals(30 + 2 * practice, result.getScheduledClasses().stream().mapToInt(WeeklyScheduleEngineTest::hours).sum());
            assertNoConflicts(result);
        }
    }

    @Test void weightsAllocateTwoEightAndEightTwoAcrossTeachersWithoutCountingLessonsAsClasses() {
        var request = request(3, 3); var c = request.getCourses().getFirst();
        c.setTotalClassesToOpen(10); c.setClassSize(20); c.setStudyEndDate(c.getStudyStartDate().plusWeeks(3).minusDays(1));
        var result = generate(request);
        assertTrue(result.isSaved(), result.getUnscheduledWarnings().toString());
        assertEquals(20, result.getTotalClassesScheduled()); assertEquals(60, result.getTotalLessonsScheduled());
        for (var allocation : result.getTeacherAllocations()) {
            assertEquals(allocation.getWeight(), allocation.getTargetClasses());
            assertEquals(allocation.getTargetClasses(), allocation.getAssignedClasses());
        }
        assertNoConflicts(result);
    }

    @Test void roundingIsAcrossAllRowsOfTheSameCourseAndCohort() {
        var request = request(1, 0);
        var copies = new ArrayList<CourseDemandDTO>();
        for (int n = 0; n < 3; n++) {
            var course = request(1, 0).getCourses().getFirst(); course.setStt(String.valueOf(n + 2)); copies.add(course);
        }
        request.setCourses(copies);
        var result = generate(request); assertTrue(result.isSaved());
        var a = result.getTeacherAllocations().stream().filter(t -> t.getTeacherId().equals("A")).findFirst().orElseThrow();
        assertEquals(1, a.getTargetClasses()); assertEquals(1, a.getAssignedClasses());
        assertEquals(3, result.getScheduledClasses().stream().map(GeneratedClassSlot::getClassId).distinct().count());
    }

    @Test void missingLabsOrZeroEligibleWeightsNeverSaveAPartialPlan() {
        var result = automatic.generateScheduleWithRooms(request(6, 6), List.of(BackendRegressionTest.room("A")));
        assertFalse(result.isSaved()); assertTrue(result.getClassWorkloads().stream().anyMatch(w -> w.getMissingPeriods() > 0));
        var request = request(1, 0); request.getCourses().getFirst().setTeachers(List.of(teacher("A", 0, 1)));
        assertFalse(generate(request).isSaved()); verifyNoInteractions(saves);
    }

    @Test void partialWeekIsAHardDateBoundaryAndReportsTheExactDeficit() {
        var request = request(7, 0); var c = request.getCourses().getFirst();
        c.setStudyStartDate(BackendRegressionTest.MONDAY.plusDays(2)); c.setStudyEndDate(c.getStudyStartDate());
        var result = generate(request); assertFalse(result.isSaved());
        assertEquals(1, result.getClassWorkloads().getFirst().getMissingPeriods());
        for (var s : result.getScheduledClasses()) assertEquals(4, s.getDayOfWeek());
        verifyNoInteractions(saves);
    }

    @Test void practiceModesAndAnExplicitWindowConstrainDatesIncludingYearBoundaries() {
        for (String mode : List.of("END", "MIDDLE", "ALTERNATE")) {
            var request = request(0, 6); var c = request.getCourses().getFirst(); c.setPracticeMode(mode);
            var result = generate(request); assertTrue(result.isSaved());
            for (var s : result.getScheduledClasses()) for (int w = 0; w < s.getWeekSchedule().length(); w++) if (s.getWeekSchedule().charAt(w) == '1') {
                if (mode.equals("END")) assertTrue(w >= 10);
                if (mode.equals("MIDDLE")) assertTrue(w >= 5 && w <= 10);
                if (mode.equals("ALTERNATE")) assertEquals(0, w % 2);
            }
        }
        var request = request(0, 6); var c = request.getCourses().getFirst();
        request.setSemesterStartDate(LocalDate.of(2026, 12, 28)); c.setStudyStartDate(request.getSemesterStartDate());
        c.setStudyEndDate(LocalDate.of(2027, 1, 30)); c.setPracticeStartDate(LocalDate.of(2027, 1, 6)); c.setPracticeEndDate(LocalDate.of(2027, 1, 12));
        var result = generate(request); assertTrue(result.isSaved());
        for (var s : result.getScheduledClasses()) for (int w = 0; w < s.getWeekSchedule().length(); w++) if (s.getWeekSchedule().charAt(w) == '1') {
            var date = request.getSemesterStartDate().plusDays(w * 7L + s.getDayOfWeek() - 2);
            assertFalse(date.isBefore(c.getPracticeStartDate())); assertFalse(date.isAfter(c.getPracticeEndDate()));
        }
    }

    @Test void existingOtherSchoolHoursAndStudentGroupsAreReserved() {
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(BackendRegressionTest.teacher("B", BackendRegressionTest.school("OTHER", "1"))));
        var result = generate(request(3, 3)); assertTrue(result.isSaved()); assertNoConflicts(result);
        assertFalse(result.getScheduledClasses().stream().anyMatch(s -> s.getTeacherId().equals("B") && s.getDayOfWeek() == 2
                && s.getStartPeriod() == 1 && s.getWeekSchedule().charAt(0) == '1'));
    }

    @Test void unrelatedReservationsDoNotExhaustTheDefaultSamplesSearchBudget() {
        var school = School.builder().schoolCode("A").schoolName("A").courses(new HashSet<>())
                .periods(new HashSet<>(BackendRegressionTest.periods().stream().map(p -> SchoolPeriod.builder()
                        .periodNumber(p.getPeriod()).startTime(p.getStart()).endTime(p.getEnd()).build()).toList())).build();
        for (int i = 0; i < 1200; i++) school.getCourses().add(TeachingCourse.builder().courseCode("ARCHIVED")
                .courseName("[LT] Archived").classroom("UNSELECTED-" + i).dayOfWeek(2).startPeriod(1)
                .numPeriods(1).weekSchedule("1".repeat(16)).build());
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(BackendRegressionTest.teacher("ARCHIVED", school)));
        var request = request(30, 30); request.getCourses().getFirst().setTotalClassesToOpen(2);
        var result = generate(request);
        assertTrue(result.isSaved(), result.getUnscheduledWarnings().toString());
        assertEquals(6, result.getTotalClassesScheduled());
        assertEquals(180, result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getScheduledPeriods).sum());
        assertNoConflicts(result);
    }

    @Test void aDifficultClassCannotSpendTheSearchBudgetOfTheFollowingClass() {
        var request = request(1000, 0);
        var difficult = request.getCourses().getFirst(); difficult.setCourseCode("AAA");
        difficult.setStudyEndDate(BackendRegressionTest.MONDAY.plusWeeks(100).minusDays(1));
        difficult.setTeachers(java.util.stream.IntStream.range(0, 100).mapToObj(i -> teacher("BUSY_" + i, 1, 0)).toList());
        var following = request(1, 0).getCourses().getFirst(); following.setCourseCode("ZZZ");
        following.setStudyEndDate(difficult.getStudyEndDate()); following.setTeachers(List.of(teacher("AVAILABLE", 1, 0)));
        request.setCourses(List.of(difficult, following));
        var result = generate(request);
        assertFalse(result.isSaved()); verifyNoInteractions(saves);
        assertTrue(result.getUnscheduledWarnings().stream().anyMatch(w -> w.contains("giới hạn tìm kiếm riêng")));
        var completed = result.getClassWorkloads().stream().filter(w -> w.getCourseCode().equals("ZZZ")).findFirst().orElseThrow();
        assertEquals(1, completed.getScheduledPeriods()); assertEquals(0, completed.getMissingPeriods());
        assertNoConflicts(result);
    }

    @Test void defaultSampleCompletesWithTheExistingDockerTimetable() throws Exception {
        // Anonymized timetable snapshot: 45 stored rows, 443 active lessons, eight rooms.
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                getClass().getResourceAsStream("/weekly-existing-reservations.json"));
        var archivedTeachers = new LinkedHashMap<String, Teacher>();
        for (var row : data.get("teachers")) {
            String id = row.get("teacherId").asText();
            archivedTeachers.put(id, Teacher.builder().teacherId(id).fullName(id)
                    .semesterStartDate(LocalDate.parse(row.get("semesterStartDate").asText())).build());
        }
        var schools = new HashMap<Long, School>();
        for (var row : data.get("schools")) {
            var teacher = archivedTeachers.get(row.get("teacherId").asText());
            var school = School.builder().schoolCode(row.get("schoolCode").asText()).schoolName("Archived")
                    .teacher(teacher).periods(new HashSet<>()).courses(new HashSet<>()).build();
            schools.put(row.get("id").asLong(), school); teacher.getSchools().add(school);
        }
        for (var row : data.get("periods")) schools.get(row.get("schoolId").asLong()).getPeriods().add(SchoolPeriod.builder()
                .periodNumber(row.get("periodNumber").asInt()).startTime(LocalTime.parse(row.get("startTime").asText()))
                .endTime(LocalTime.parse(row.get("endTime").asText())).build());
        for (var row : data.get("courses")) {
            var course = TeachingCourse.builder().courseCode(row.get("courseCode").asText()).courseName(row.get("courseName").asText())
                    .classroom(row.get("classroom").asText()).dayOfWeek(row.get("dayOfWeek").asInt())
                    .startPeriod(row.get("startPeriod").asInt()).numPeriods(row.get("numPeriods").asInt())
                    .weekSchedule(row.get("weekSchedule").asText()).sourceType(row.get("sourceType").asText()).build();
            if (!row.get("sessionType").isNull()) course.setSessionType(row.get("sessionType").asText());
            if (!row.get("customStartTime").isNull()) course.setCustomStartTime(LocalTime.parse(row.get("customStartTime").asText()));
            if (!row.get("customEndTime").isNull()) course.setCustomEndTime(LocalTime.parse(row.get("customEndTime").asText()));
            schools.get(row.get("schoolId").asLong()).getCourses().add(course);
        }
        var available = new ArrayList<Room>();
        for (var row : data.get("rooms")) available.add(Room.builder().schoolCode(row.get("schoolCode").asText())
                .roomCode(row.get("roomCode").asText()).roomType(row.get("roomType").asText())
                .capacity(row.get("capacity").asInt()).isLab(row.get("lab").asInt() == 1).build());
        when(teachers.findAllWithFullSchedule()).thenReturn(new ArrayList<>(archivedTeachers.values()));
        var request = request(30, 30); request.getTargetSchool().setSchoolId("PTIT");
        request.getTargetSchool().getPeriods().set(5, new PeriodTimeDTO(6, LocalTime.NOON, LocalTime.of(13, 0)));
        var course = request.getCourses().getFirst(); course.setCourseCode("61FIT3DBS"); course.setCourseName("Cơ sở dữ liệu");
        course.setTotalClassesToOpen(2); course.setClassSize(50);
        course.setTeachers(List.of(teacher("GV01", 2, 8), teacher("GV02", 8, 2)));
        var manualRooms = available.stream().filter(r -> Set.of("101-A2", "301-A2", "601-LAB-A2", "602-LAB-A2").contains(r.getRoomCode())).toList();
        for (var pool : List.of(available, manualRooms)) {
            var result = automatic.generateScheduleWithRooms(request, pool);
            assertTrue(result.isSaved(), "Room pool " + pool.size() + ": " + result.getUnscheduledWarnings());
            assertEquals(6, result.getTotalClassesScheduled());
            assertEquals(180, result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getScheduledPeriods).sum());
            assertTrue(result.getClassWorkloads().stream().allMatch(w -> w.getMissingPeriods() == 0));
            assertNoConflicts(result);
        }
    }

    static void assertNoConflicts(AutoScheduleResponse result) {
        var slots = result.getScheduledClasses();
        for (int i = 0; i < slots.size(); i++) for (int j = i + 1; j < slots.size(); j++) {
            var a = slots.get(i); var b = slots.get(j);
            boolean teacher = a.getTeacherId().equals(b.getTeacherId());
            if (teacher && !a.getSessionType().equals(b.getSessionType())) assertNotEquals(a.getDayOfWeek(), b.getDayOfWeek());
            for (int w = 0; w < Math.min(a.getWeekSchedule().length(), b.getWeekSchedule().length()); w++) {
                if (a.getWeekSchedule().charAt(w) != '1' || b.getWeekSchedule().charAt(w) != '1' || a.getDayOfWeek() != b.getDayOfWeek()) continue;
                boolean overlapping = a.getStartPeriod() < b.getStartPeriod() + b.getNumPeriods() && b.getStartPeriod() < a.getStartPeriod() + a.getNumPeriods();
                boolean students = a.getParentClassId().equals(b.getParentClassId()) && (a.getSessionType().equals("THEORY") || b.getSessionType().equals("THEORY") || a.getClassId().equals(b.getClassId()));
                assertFalse(overlapping && (teacher || students || a.getRoom().equals(b.getRoom())));
            }
        }
    }
}
