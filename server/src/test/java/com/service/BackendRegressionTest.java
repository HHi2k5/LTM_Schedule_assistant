package com.service;

import com.dto.*;
import com.model.*;
import com.repository.*;
import com.security.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackendRegressionTest {
    static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);
    final TeacherRepository teachers = mock(TeacherRepository.class);
    final SchoolRepository schools = mock(SchoolRepository.class);
    final ScheduleService saves = mock(ScheduleService.class);
    final ScheduleWriteLock lock = mock(ScheduleWriteLock.class);
    final AutoScheduleService automatic = new AutoScheduleService(teachers, mock(RoomRepository.class), saves, lock);
    final ScheduleService schedules = new ScheduleService(teachers, schools, mock(ScheduleAccess.class), lock);

    @Test void twoSchoolsWithSameNamedSimultaneousLessonAreBothReturnedAndFlagged() {
        var teacher = teacher("GV01", school("A", "1"), school("B", "1"));
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher));
        var result = schedules.getWeeklySchedule("GV01", 1);
        assertEquals(2, result.getLessons().size());
        assertTrue(result.getLessons().stream().allMatch(LessonSlot::isConflict));
    }
    @Test void joinedDuplicatesUseEntityIdAndAdjacentLessonsDoNotConflict() {
        var a = school("A", "1"); var course = a.getCourses().iterator().next(); course.setId(1L);
        var b = school("B", "1"); b.getCourses().iterator().next().setId(2L);
        b.getPeriods().iterator().next().setStartTime(LocalTime.of(7, 50)); b.getPeriods().iterator().next().setEndTime(LocalTime.of(8, 40));
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher("GV01", a, a, b)));
        var result = schedules.getWeeklySchedule("GV01", 1);
        assertEquals(2, result.getLessons().size());
        assertTrue(result.getLessons().stream().noneMatch(LessonSlot::isConflict));
    }
    @Test void weeklyScheduleIncludesAllRealPeriodsEvenWithoutLessonsInThosePeriodsOrWeek() {
        var a = school("A", "1");
        a.getPeriods().clear();
        for (var period : periods()) a.getPeriods().add(SchoolPeriod.builder()
                .periodNumber(period.getPeriod()).startTime(period.getStart()).endTime(period.getEnd()).build());
        var course = a.getCourses().iterator().next(); course.setStartPeriod(4); course.setNumPeriods(3);
        var b = school("B", "-1");
        b.getPeriods().iterator().next().setStartTime(LocalTime.of(7, 20));
        b.getPeriods().iterator().next().setEndTime(LocalTime.of(8, 10));
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher("GV01", b, a, a)));

        var result = schedules.getWeeklySchedule("GV01", 1);
        assertEquals(1, result.getLessons().size());
        assertEquals(4, result.getLessons().getFirst().getStartPeriod());
        assertEquals(List.of("A", "B"), result.getSchools().stream().map(SchoolConfigDTO::getSchoolId).toList());
        assertEquals(periods(), result.getSchools().getFirst().getPeriods());
        assertEquals(LocalTime.of(7, 20), result.getSchools().getLast().getPeriods().getFirst().getStart());

        var emptyWeek = schedules.getWeeklySchedule("GV01", 3);
        assertTrue(emptyWeek.getLessons().isEmpty());
        assertEquals(result.getSchools(), emptyWeek.getSchools());
    }
    @Test void teacherWithoutSchoolsHasEmptyPeriodTablesAndLessons() {
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher("GV01")));
        var result = schedules.getWeeklySchedule("GV01", 1);
        assertEquals(List.of(), result.getSchools());
        assertEquals(List.of(), result.getLessons());
    }
    @Test void changedAnchorIsRejectedBeforeSavingAndOldDateIsPreserved() {
        var teacher = teacher("GV01", school("A", "1"));
        when(teachers.findById("GV01")).thenReturn(Optional.of(teacher));
        var input = upload(); input.setSemesterStartDate(MONDAY.plusWeeks(1));
        assertThrows(IllegalArgumentException.class, () -> schedules.saveOrUpdateSchoolSchedule(input));
        assertEquals(MONDAY, teacher.getSemesterStartDate()); verify(teachers, never()).save(any());
    }
    @Test void emptyReplacementAndInvalidWeekAreRejected() {
        var input = upload(); input.setCourses(List.of());
        assertThrows(IllegalArgumentException.class, () -> schedules.saveOrUpdateSchoolSchedule(input));
        assertThrows(IllegalArgumentException.class, () -> schedules.getWeeklySchedule("GV01", 0));
        verifyNoInteractions(teachers, schools);
    }
    @Test void missingPeriodDoesNotInventTimes() {
        var school = school("A", "1"); school.getPeriods().clear();
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher("GV01", school)));
        assertThrows(IllegalArgumentException.class, () -> schedules.getWeeklySchedule("GV01", 1));
    }
    @Test void oneClassWithTwoUnassignedTeachersOpensOnlyOneClass() {
        var input = request(); input.getCourses().getFirst().setTeachers(List.of(candidate("GV01", null), candidate("GV02", null)));
        var result = automatic.generateScheduleWithRooms(input, List.of(room("A")));
        assertEquals(1, result.getScheduledClasses().size()); assertTrue(result.isSaved());
    }
    @Test void explicitQuotasArePreservedAndInconsistentQuotasRejected() {
        var input = request(); var course = input.getCourses().getFirst(); course.setTotalClassesToOpen(4);
        course.setTeachers(List.of(candidate("GV01", 1), candidate("GV02", 3)));
        var result = automatic.generateScheduleWithRooms(input, List.of(room("A")));
        assertEquals(1, result.getScheduledClasses().stream().filter(s -> s.getTeacherId().equals("GV01")).count());
        assertEquals(3, result.getScheduledClasses().stream().filter(s -> s.getTeacherId().equals("GV02")).count());
        course.setTotalClassesToOpen(5);
        assertThrows(IllegalArgumentException.class, () -> automatic.generateScheduleWithRooms(input, List.of(room("A"))));
    }
    @Test void noRoomOrPartialResultNeverOverwritesExistingSchedule() {
        var result = automatic.generateScheduleWithRooms(request(), List.of());
        assertFalse(result.isSaved()); verifyNoInteractions(saves);
        var input = request(); input.getCourses().getFirst().setHasPractice(true);
        result = automatic.generateScheduleWithRooms(input, List.of(room("A")));
        assertFalse(result.isSaved()); assertFalse(result.getScheduledClasses().isEmpty()); verifyNoInteractions(saves);
    }
    @Test void candidateCannotOverrunPeriodTable() {
        var input = request(); input.getCourses().getFirst().setFixedStartPeriod(12); input.getCourses().getFirst().setNumPeriodsPerSession(5);
        var result = automatic.generateScheduleWithRooms(input, List.of(room("A")));
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getStartPeriod() + s.getNumPeriods() - 1 <= 12));
    }
    @Test void otherSchoolsRoomsAreNotUsed() {
        assertFalse(automatic.generateScheduleWithRooms(request(), List.of(room("B"))).isSaved());
        verifyNoInteractions(saves);
    }
    @Test void existingOtherTeachersRoomBookingIsRespected() {
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(teacher("GV02", school("A", "1"))));
        var result = automatic.generateScheduleWithRooms(request(), List.of(room("A")));
        assertFalse(result.getScheduledClasses().stream().anyMatch(s -> s.getDayOfWeek() == 2 && s.getStartPeriod() == 1));
    }
    @Test void sameTeacherCanUseSameHoursWhenWeeksDoNotOverlap() {
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(teacher("GV01", school("B", "-1"))));
        var input = request(); input.setTotalWeeks(1);
        var slot = automatic.generateScheduleWithRooms(input, List.of(room("A"))).getScheduledClasses().getFirst();
        assertEquals(2, slot.getDayOfWeek()); assertEquals(1, slot.getStartPeriod());
    }
    @Test void sameTeacherCannotUseOverlappingWeeksAtAnotherSchool() {
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(teacher("GV01", school("B", "1"))));
        var slot = automatic.generateScheduleWithRooms(request(), List.of(room("A"))).getScheduledClasses().getFirst();
        assertFalse(slot.getDayOfWeek() == 2 && slot.getStartPeriod() == 1);
    }
    @Test void existingAnchorMismatchRejectsWholeAutomaticRun() {
        var teacher = teacher("GV01", school("B", "1")); teacher.setSemesterStartDate(MONDAY.plusWeeks(1));
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(teacher));
        assertThrows(IllegalArgumentException.class, () -> automatic.generateScheduleWithRooms(request(), List.of(room("A"))));
        verifyNoInteractions(saves);
    }

    static List<PeriodTimeDTO> periods() {
        var values = new ArrayList<PeriodTimeDTO>();
        for (int p = 1; p <= 12; p++) values.add(new PeriodTimeDTO(p, LocalTime.of(6 + p, 0), LocalTime.of(6 + p, 50)));
        return values;
    }
    static School school(String code, String weeks) {
        return School.builder().schoolCode(code).schoolName(code)
                .periods(new HashSet<>(Set.of(SchoolPeriod.builder().periodNumber(1).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(7, 50)).build())))
                .courses(new HashSet<>(Set.of(TeachingCourse.builder().courseCode("NET").courseName("Networking").classroom("101")
                        .dayOfWeek(2).startPeriod(1).numPeriods(1).weekSchedule(weeks).build()))).build();
    }
    static Teacher teacher(String id, School... schools) {
        return Teacher.builder().teacherId(id).semesterStartDate(MONDAY).schools(new ArrayList<>(List.of(schools))).build();
    }
    static Room room(String school) { return Room.builder().roomCode("101").roomType("THEORY_STANDARD").capacity(60).schoolCode(school).build(); }
    static TeacherCandidateDTO candidate(String id, Integer quota) { return TeacherCandidateDTO.builder().teacherId(id).teacherName(id).assignedClasses(quota).build(); }
    static SchoolWideScheduleRequest request() {
        return SchoolWideScheduleRequest.builder().semesterStartDate(MONDAY).totalWeeks(16)
                .targetSchool(SchoolConfigDTO.builder().schoolId("A").schoolName("A").periods(periods()).build())
                .courses(List.of(CourseDemandDTO.builder().courseCode("NET").courseName("Networking").totalClassesToOpen(1)
                        .classSize(50).numPeriodsPerSession(3).fixedDayOfWeek(2).fixedStartPeriod(1)
                        .hasPractice(false).teachers(List.of(candidate("GV01", null))).build())).build();
    }
    static SchoolUploadRequest upload() {
        return SchoolUploadRequest.builder().teacherId("GV01").semesterStartDate(MONDAY)
                .school(SchoolConfigDTO.builder().schoolId("B").schoolName("B").periods(periods()).build())
                .courses(List.of(TeachingCourseDTO.builder().courseCode("NET").courseName("Networking").room("101")
                        .dayOfWeek(2).startPeriod(1).numPeriods(1).weekSchedule("1").build())).build();
    }
}
