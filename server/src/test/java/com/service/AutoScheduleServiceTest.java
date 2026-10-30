package com.service;

import com.dto.*;
import com.repository.TeacherRepository;
import com.repository.RoomRepository;
import com.model.Room;
import com.model.School;
import com.model.SchoolPeriod;
import com.model.Teacher;
import com.model.TeachingCourse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AutoScheduleServiceTest {
    @Test void teacherPracticeAvoidsTheoryDaysOfEveryClassInThePlan() {
        var request = practiceRequest(1, 50, 16, 1);
        request.getCourses().getFirst().setFixedDayOfWeek(2);
        var other = practiceRequest(1, 150, 16, 1).getCourses().getFirst();
        other.setFixedDayOfWeek(4);
        request.setCourses(List.of(request.getCourses().getFirst(), other));
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        assertEquals(Set.of(2, 4), new HashSet<>(result.getScheduledClasses().stream()
                .filter(s -> "THEORY".equals(s.getSessionType())).map(GeneratedClassSlot::getDayOfWeek).toList()));
        assertEquals(7, practiceSlots(result).size());
        assertNoConflicts(result);
    }

    @Test void teacherPracticeAlsoAvoidsOtherSubjectsAndTheoryOnlyClasses() {
        var request = practiceRequest(1, 50, 16, 1);
        request.getCourses().getFirst().setFixedDayOfWeek(2);
        var other = practiceRequest(1, 50, 16, 1).getCourses().getFirst();
        other.setCourseCode("DB"); other.setCourseName("Databases"); other.setFixedDayOfWeek(3); other.setHasPractice(false);
        request.setCourses(List.of(request.getCourses().getFirst(), other));
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        for (var practice : practiceSlots(result)) assertEquals(4, practice.getDayOfWeek());
        assertNoConflicts(result);
    }

    @Test void differentTeachersMayTeachDifferentSessionTypesOnTheSameDay() {
        var request = practiceRequest(1, 50, 16, 1);
        var other = practiceRequest(1, 50, 16, 1).getCourses().getFirst();
        other.setFixedDayOfWeek(3); other.setHasPractice(false);
        other.setTeachers(List.of(TeacherCandidateDTO.builder().teacherId("GV02").teacherName("Other teacher").build()));
        request.setCourses(List.of(request.getCourses().getFirst(), other));
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        assertTrue(result.getScheduledClasses().stream().anyMatch(s -> s.getTeacherId().equals("GV02") && s.getDayOfWeek() == 3));
        for (var practice : practiceSlots(result)) assertEquals(3, practice.getDayOfWeek());
        assertNoConflicts(result);
    }

    @Test void practiceCannotShareADayWithExistingTheoryAtAnotherSchoolEvenAtDifferentHours() {
        var request = practiceRequest(1, 60, 16, 1);
        var teachers = mock(TeacherRepository.class);
        var existing = blockedDays(request, "GV01", "OTHER", "101", 3, 3);
        var course = existing.getSchools().getFirst().getCourses().iterator().next();
        course.setStartPeriod(7); course.setNumPeriods(3); course.setCourseName("[LT] Existing theory");
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(existing));
        var result = service(teachers, mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        for (var practice : practiceSlots(result)) assertEquals(4, practice.getDayOfWeek());
        assertNoConflicts(result);
    }

    @Test void newTheoryCannotShareADayWithExistingPracticeAtAnotherSchoolEvenAtDifferentHours() {
        var request = practiceRequest(1, 60, 16, 1);
        var teachers = mock(TeacherRepository.class);
        var existing = blockedDays(request, "GV01", "OTHER", "LAB", 2, 2);
        var course = existing.getSchools().getFirst().getCourses().iterator().next();
        course.setStartPeriod(7); course.setNumPeriods(3); course.setCourseName("[TH] Existing practice");
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(existing));
        var result = service(teachers, mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        assertTrue(result.getScheduledClasses().stream().filter(s -> "THEORY".equals(s.getSessionType())).allMatch(s -> s.getDayOfWeek() == 3));
        for (var practice : practiceSlots(result)) assertEquals(2, practice.getDayOfWeek());
        assertNoConflicts(result);
    }

    @Test void teacherWithTheoryEveryAvailableDayCannotSavePracticeOnAnotherClassesTheoryDay() {
        var request = practiceRequest(1, 60, 4, 1);
        var courses = new ArrayList<CourseDemandDTO>();
        for (int day = 2; day <= 7; day++) {
            var course = practiceRequest(1, 60, 4, 1).getCourses().getFirst();
            course.setFixedDayOfWeek(day); course.setHasPractice(day == 2);
            courses.add(course);
        }
        request.setCourses(courses);
        var saves = mock(ScheduleService.class);
        var result = service(mock(TeacherRepository.class), saves).generateScheduleWithRooms(request, practiceRooms());
        assertFalse(result.isSaved());
        assertEquals(6, result.getScheduledClasses().size());
        assertTrue(practiceSlots(result).isEmpty());
        assertEquals(2, result.getUnscheduledWarnings().size());
        verifyNoInteractions(saves);
    }

    @Test void practiceUsesADifferentDayEvenWhenTheoryDayHasFreeShifts() {
        var request = practiceRequest(1, 60, 16, 1);
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        var theory = result.getScheduledClasses().stream().filter(s -> "THEORY".equals(s.getSessionType())).findFirst().orElseThrow();
        assertEquals(2, theory.getDayOfWeek());
        for (var practice : practiceSlots(result)) {
            assertEquals(3, practice.getDayOfWeek());
            assertEquals(List.of(2, 4), activeWeeks(practice.getWeekSchedule()));
        }
        assertNoConflicts(result);
    }

    @Test void noAvailablePracticeDayDoesNotFallBackToTheoryDayOrSaveAPartialSchedule() {
        var request = practiceRequest(1, 60, 4, 1);
        var teachers = mock(TeacherRepository.class);
        var saves = mock(ScheduleService.class);
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(blockedDays(request, "GV02", "PTIT", "LAB", 3, 7)));
        var result = service(teachers, saves).generateScheduleWithRooms(request, practiceRooms());
        assertFalse(result.isSaved());
        assertEquals(1, result.getScheduledClasses().size());
        assertTrue(practiceSlots(result).isEmpty());
        assertTrue(result.getUnscheduledWarnings().stream().allMatch(w -> w.contains("ngày khác lý thuyết")));
        verifyNoInteractions(saves);
    }

    @Test void practiceAvoidsTheActualTheoryDayAfterTheoryMovesToAnotherDay() {
        var request = practiceRequest(1, 60, 16, 1);
        request.getCourses().getFirst().setFixedDayOfWeek(2);
        var teachers = mock(TeacherRepository.class);
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(blockedDays(request, "GV01", "OTHER", "101", 2, 2)));
        var result = service(teachers, mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        var theory = result.getScheduledClasses().stream().filter(s -> "THEORY".equals(s.getSessionType())).findFirst().orElseThrow();
        assertEquals(3, theory.getDayOfWeek());
        for (var practice : practiceSlots(result)) assertEquals(4, practice.getDayOfWeek());
        assertNoConflicts(result);
    }

    private Teacher blockedDays(SchoolWideScheduleRequest request, String teacherId, String schoolCode, String room, int first, int last) {
        var courses = new HashSet<TeachingCourse>();
        for (int day = first; day <= last; day++) courses.add(TeachingCourse.builder().courseCode("BUSY").courseName("Existing class")
                .classroom(room).dayOfWeek(day).startPeriod(1).numPeriods(12).weekSchedule("1".repeat(request.getTotalWeeks())).build());
        var periods = new HashSet<SchoolPeriod>();
        for (var period : request.getTargetSchool().getPeriods()) periods.add(SchoolPeriod.builder()
                .periodNumber(period.getPeriod()).startTime(period.getStart()).endTime(period.getEnd()).build());
        return Teacher.builder().teacherId(teacherId).semesterStartDate(request.getSemesterStartDate())
                .schools(List.of(School.builder().schoolCode(schoolCode).schoolName(schoolCode).periods(periods).courses(courses).build())).build();
    }

    @Test void eachPracticeGroupHasExactlyTwoLessonsTwoWeeksApartAndSavesTheSameWeeks() {
        var saves = mock(ScheduleService.class);
        var result = service(mock(TeacherRepository.class), saves).generateScheduleWithRooms(practiceRequest(1, 61, 16, 1), practiceRooms());
        assertTrue(result.isSaved());
        var practice = practiceSlots(result);
        assertEquals(3, practice.size());
        assertEquals(61, practice.stream().mapToInt(GeneratedClassSlot::getStudentCount).sum());
        for (var slot : practice) {
            assertEquals(List.of(2, 4), activeWeeks(slot.getWeekSchedule()));
            assertEquals(1, slot.getWeekSchedule().chars().filter(c -> c == '1').count());
            assertEquals(1, slot.getWeekSchedule().chars().filter(c -> c == '2').count());
            assertTrue(slot.getPhaseNote().contains("Bài 1: tuần 2; Bài 2: tuần 4"));
            assertTrue(slot.getStudentCount() <= slot.getRoomCapacity());
        }
        assertNoConflicts(result);
        var captured = ArgumentCaptor.forClass(SchoolUploadRequest.class);
        verify(saves).saveOrUpdateSchoolSchedule(captured.capture());
        assertEquals(practice.stream().map(GeneratedClassSlot::getWeekSchedule).toList(), captured.getValue().getCourses().stream()
                .filter(course -> course.getCourseName().startsWith("[TH]")).map(TeachingCourseDTO::getWeekSchedule).toList());
    }

    @Test void sameTeacherClassesAlternateWeeksAndReuseTheSamePracticeShifts() {
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class))
                .generateScheduleWithRooms(practiceRequest(2, 60, 16, 1), practiceRooms());
        assertTrue(result.isSaved());
        assertEquals(4, practiceSlots(result).size());
        for (int sub = 1; sub <= 2; sub++) {
            String suffix = " - Tổ " + sub;
            var first = practiceSlots(result).stream().filter(s -> s.getClassGroup().equals("Lớp 01" + suffix)).findFirst().orElseThrow();
            var second = practiceSlots(result).stream().filter(s -> s.getClassGroup().equals("Lớp 02" + suffix)).findFirst().orElseThrow();
            assertEquals(List.of(2, 4), activeWeeks(first.getWeekSchedule()));
            assertEquals(List.of(3, 5), activeWeeks(second.getWeekSchedule()));
            assertEquals(first.getTeacherId(), second.getTeacherId());
            assertEquals(first.getDayOfWeek(), second.getDayOfWeek());
            assertEquals(first.getStartPeriod(), second.getStartPeriod());
            assertEquals(first.getRoom(), second.getRoom());
        }
        assertNoConflicts(result);
    }

    @Test void rotationContinuesAcrossSeparatePlanRowsForTheSameTeacher() {
        var request = practiceRequest(1, 50, 16, 1);
        var other = practiceRequest(1, 50, 16, 1).getCourses().getFirst();
        request.setCourses(List.of(request.getCourses().getFirst(), other));
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        assertEquals(List.of(List.of(2, 4), List.of(2, 4), List.of(3, 5), List.of(3, 5)),
                practiceSlots(result).stream().map(s -> activeWeeks(s.getWeekSchedule())).toList());
        assertNoConflicts(result);
    }

    @Test void shortSemesterDoesNotSaveASinglePracticeLessonAsComplete() {
        var saves = mock(ScheduleService.class);
        var result = service(mock(TeacherRepository.class), saves).generateScheduleWithRooms(practiceRequest(1, 50, 16, 14), practiceRooms());
        assertFalse(result.isSaved());
        assertEquals("NOT_SAVED", result.getStatus());
        assertTrue(practiceSlots(result).isEmpty());
        assertEquals(2, result.getUnscheduledWarnings().size());
        assertTrue(result.getUnscheduledWarnings().getFirst().contains("2 buổi thực hành cách tuần"));
        verifyNoInteractions(saves);
    }

    @Test void classesStartingInDifferentWeeksStillAlternateCalendarWeekPhases() {
        var request = practiceRequest(1, 50, 16, 2);
        var next = practiceRequest(1, 50, 16, 3).getCourses().getFirst();
        request.setCourses(List.of(request.getCourses().getFirst(), next));
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class)).generateScheduleWithRooms(request, practiceRooms());
        assertTrue(result.isSaved());
        assertEquals(List.of(List.of(3, 5), List.of(3, 5), List.of(4, 6), List.of(4, 6)),
                practiceSlots(result).stream().map(s -> activeWeeks(s.getWeekSchedule())).toList());
        assertNoConflicts(result);
    }

    @Test void practiceCanUseTheLastWeekButNeverContinuesPastTheSecondLesson() {
        var result = service(mock(TeacherRepository.class), mock(ScheduleService.class))
                .generateScheduleWithRooms(practiceRequest(1, 150, 16, 13), practiceRooms());
        assertTrue(result.isSaved());
        assertEquals(5, practiceSlots(result).size());
        for (var slot : practiceSlots(result)) assertEquals(List.of(14, 16), activeWeeks(slot.getWeekSchedule()));
        assertNoConflicts(result);
    }

    private AutoScheduleService service(TeacherRepository teachers, ScheduleService saves) {
        return new AutoScheduleService(teachers, mock(RoomRepository.class), saves, mock(ScheduleWriteLock.class));
    }

    private SchoolWideScheduleRequest practiceRequest(int classes, int size, int totalWeeks, int startWeek) {
        var periods = new ArrayList<PeriodTimeDTO>();
        for (int p = 1; p <= 12; p++) periods.add(new PeriodTimeDTO(p, LocalTime.of(6 + p, 0), LocalTime.of(6 + p, 50)));
        return SchoolWideScheduleRequest.builder().semesterStartDate(LocalDate.of(2026, 8, 10)).totalWeeks(totalWeeks)
                .targetSchool(SchoolConfigDTO.builder().schoolId("PTIT").schoolName("School").periods(periods).build())
                .courses(List.of(CourseDemandDTO.builder().courseCode("NET").courseName("Networking").classSize(size)
                        .totalClassesToOpen(classes).startWeek(startWeek).numPeriodsPerSession(3).hasPractice(true)
                        .teachers(List.of(TeacherCandidateDTO.builder().teacherId("GV01").teacherName("Teacher").build())).build())).build();
    }

    private List<Room> practiceRooms() {
        var theory = new Room(); theory.setRoomCode("101"); theory.setCapacity(200); theory.setSchoolCode("PTIT");
        var lab = new Room(); lab.setRoomCode("LAB"); lab.setCapacity(30); lab.setSchoolCode("PTIT"); lab.setLab(true);
        return List.of(theory, lab);
    }

    private List<GeneratedClassSlot> practiceSlots(AutoScheduleResponse result) {
        return result.getScheduledClasses().stream().filter(slot -> "PRACTICE".equals(slot.getSessionType())).toList();
    }

    private List<Integer> activeWeeks(String weeks) {
        var active = new ArrayList<Integer>();
        for (int i = 0; i < weeks.length(); i++) if (weeks.charAt(i) != '-' && weeks.charAt(i) != ' ') active.add(i + 1);
        return active;
    }

    private void assertNoConflicts(AutoScheduleResponse result) {
        var slots = result.getScheduledClasses();
        for (var practice : practiceSlots(result)) {
            for (var theory : slots) {
                if ("THEORY".equals(theory.getSessionType()) && theory.getTeacherId().equals(practice.getTeacherId())) {
                    assertNotEquals(theory.getDayOfWeek(), practice.getDayOfWeek(), practice.getClassGroup() + " shares a theory day for " + practice.getTeacherId());
                }
            }
        }
        for (int i = 0; i < slots.size(); i++) for (int j = i + 1; j < slots.size(); j++) {
            var a = slots.get(i); var b = slots.get(j);
            boolean overlap = a.getDayOfWeek() == b.getDayOfWeek()
                    && a.getStartPeriod() < b.getStartPeriod() + b.getNumPeriods()
                    && b.getStartPeriod() < a.getStartPeriod() + a.getNumPeriods()
                    && activeWeeks(a.getWeekSchedule()).stream().anyMatch(activeWeeks(b.getWeekSchedule())::contains);
            if (a.getTeacherId().equals(b.getTeacherId()) || a.getRoom().equals(b.getRoom())) {
                assertFalse(overlap, a.getClassGroup() + " conflicts with " + b.getClassGroup());
            }
        }
    }

    @Test void generatedSessionsKeepOriginalNameCreditsAndExportContext() {
        var teachers = mock(TeacherRepository.class);
        when(teachers.findTeacherWithFullSchedule(anyString())).thenReturn(Optional.empty());
        var rooms = mock(RoomRepository.class);
        var theory = new Room(); theory.setRoomCode("101-A2"); theory.setCapacity(60); theory.setLab(false); theory.setSchoolCode("PTIT");
        var lab = new Room(); lab.setRoomCode("LAB-A2"); lab.setCapacity(30); lab.setLab(true); lab.setSchoolCode("PTIT");
        when(rooms.findBySchoolCodeOrderByCapacityAsc("PTIT")).thenReturn(List.of(theory, lab));
        var service = new AutoScheduleService(teachers, rooms, mock(ScheduleService.class), mock(ScheduleWriteLock.class));
        List<PeriodTimeDTO> periods = new ArrayList<>();
        for (int p = 1; p <= 10; p++) periods.add(new PeriodTimeDTO(p, LocalTime.of(6 + p, 0), LocalTime.of(6 + p, 50)));
        var request = SchoolWideScheduleRequest.builder().semesterStartDate(LocalDate.of(2026, 8, 10))
                .totalWeeks(16).buildingSuffix("A2")
                .targetSchool(SchoolConfigDTO.builder().schoolId("PTIT").schoolName("School").periods(periods).build())
                .courses(List.of(CourseDemandDTO.builder().courseCode("INT1313").courseName("Networking").credits(3)
                        .totalClassesToOpen(1).teachers(List.of(TeacherCandidateDTO.builder().teacherId("GV01")
                                .teacherName("Teacher").assignedClasses(1).build())).build())).build();
        var result = service.generateSchoolWideSchedule(request);
        assertEquals("PTIT", result.getSchoolId()); assertEquals(request.getSemesterStartDate(), result.getSemesterStartDate());
        assertEquals(periods, result.getPeriods()); assertFalse(result.getScheduledClasses().isEmpty());
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> "Networking".equals(slot.getBaseCourseName()) && slot.getCredits() == 3));
        assertEquals(Set.of("THEORY", "PRACTICE"), new HashSet<>(result.getScheduledClasses().stream().map(GeneratedClassSlot::getSessionType).toList()));
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> slot.getNumPeriods() == 3));
    }
}
