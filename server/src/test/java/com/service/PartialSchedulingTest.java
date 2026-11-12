package com.service;

import com.dto.*;
import com.model.Room;
import com.model.TeacherSubject;
import com.repository.TeacherRepository;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartialSchedulingTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private TeacherRepository teachers;
    private ScheduleService schedules;

    @BeforeEach
    void setUp() {
        teachers = mock(TeacherRepository.class);
        schedules = mock(ScheduleService.class);
        when(teachers.findAllWithFullSchedule()).thenReturn(List.of());
    }

    @Test
    void excelWithoutAnAssignmentContinuesAndSavesTheOtherCourse() throws Exception {
        var assigned = TeacherSubject.builder().teacherId("GV01").teacherName("Teacher")
                .courseCode("Z_ASSIGNED").cohort("2024").theoryWeight(1).practiceWeight(1).build();
        var courses = new WeeklyPlanExcelReader().read(planWorkbook(), null, List.of(assigned));
        assertEquals(2, courses.size());
        assertTrue(courses.getFirst().getTeachers().isEmpty());

        var request = request(courses);
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request, rooms());
        assertTrue(result.isSaved());
        assertEquals("PARTIAL_SUCCESS", result.getStatus());
        assertEquals(1, result.getTotalClassesScheduled());
        assertEquals(1, result.getUnscheduledClasses().size());
        var missing = result.getUnscheduledClasses().getFirst();
        assertEquals("A_MISSING", missing.getCourseCode());
        assertEquals("Missing course", missing.getCourseName());
        assertEquals("2024", missing.getCohort());
        assertEquals("NO_TEACHER", missing.getReasonCode());
        assertNull(missing.getTeacherId());
        assertEquals(3, missing.getMissingPeriods());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getCourseCode().equals("Z_ASSIGNED")));
        verify(schedules).replaceWeeklyGenerated(eq(request), argThat(map -> map.size() == 1
                && map.get("GV01").stream().allMatch(s -> s.getCourseCode().equals("Z_ASSIGNED"))), anyMap());
    }

    @Test
    void anIncompleteClassDoesNotHoldTheRoomOrTeacherForTheFollowingClass() {
        var request = request(List.of(course("A_TOO_LARGE", 30, List.of(candidate(1))),
                course("Z_FITS", 3, List.of(candidate(1)))));
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request, rooms());
        assertEquals("PARTIAL_SUCCESS", result.getStatus());
        assertEquals(1, result.getTotalClassesScheduled());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getCourseCode().equals("Z_FITS")));
        var missing = result.getUnscheduledClasses().getFirst();
        assertEquals("NO_AVAILABLE_SLOT", missing.getReasonCode());
        assertEquals(0, missing.getScheduledPeriods());
        assertEquals(30, missing.getMissingPeriods());
        assertNull(missing.getTeacherId());
        assertEquals(3, result.getTeacherAllocations().stream().mapToInt(TeacherAllocationDTO::getScheduledPeriods).sum());
        verify(schedules).replaceWeeklyGenerated(eq(request), anyMap(), anyMap());
    }

    @Test
    void zeroWeightIsReportedWithoutStoppingOtherClasses() {
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(
                course("A_ZERO", 3, List.of(candidate(0))), course("Z_FITS", 3, List.of(candidate(1))))), rooms());
        assertEquals("NO_TEACHER", result.getUnscheduledClasses().getFirst().getReasonCode());
        assertEquals(1, result.getTotalClassesScheduled());
        assertTrue(result.isSaved());
    }

    @Test
    void allUnassignedClassesReturnAReportAndDoNotReplaceExistingSchedules() {
        var absent = course("A_MISSING", 3, null);
        absent.setTotalClassesToOpen(2);
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(absent)), rooms());
        assertFalse(result.isSaved());
        assertEquals("NOT_SAVED", result.getStatus());
        assertEquals(2, result.getUnscheduledClasses().size());
        assertTrue(result.getScheduledClasses().isEmpty());
        verifyNoInteractions(schedules);
    }

    @Test
    void fullyScheduledResultRemainsSuccess() {
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(
                request(List.of(course("FITS", 3, List.of(candidate(1))))), rooms());
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.isSaved());
        assertTrue(result.getUnscheduledClasses().isEmpty());
        assertTrue(result.getUnscheduledWarnings().isEmpty());
    }

    @Test
    void missingRoomIsReportedSeparatelyFromMissingTeacher() {
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(
                request(List.of(course("NO_ROOM", 3, List.of(candidate(1))))), List.of());
        assertEquals("NO_ROOM", result.getUnscheduledClasses().getFirst().getReasonCode());
        assertFalse(result.isSaved());
        verifyNoInteractions(schedules);
    }

    private static TeacherCandidateDTO candidate(int weight) {
        return TeacherCandidateDTO.builder().teacherId("GV01").teacherName("Teacher")
                .theoryWeight(weight).practiceWeight(1).build();
    }

    private static CourseDemandDTO course(String code, int required, List<TeacherCandidateDTO> candidates) {
        return CourseDemandDTO.builder().stt("1").courseCode(code).courseName(code).cohort("2024")
                .credits(3).theoryPeriods(required).practicePeriods(0).totalClassesToOpen(1).classSize(30)
                .studyStartDate(START).studyEndDate(START).teachers(candidates).build();
    }

    private static SchoolWideScheduleRequest request(List<CourseDemandDTO> courses) {
        return SchoolWideScheduleRequest.builder().semesterStartDate(START).totalWeeks(1).courses(courses)
                .targetSchool(SchoolConfigDTO.builder().schoolId("PTIT").schoolName("School")
                        .periods(List.of(new PeriodTimeDTO(1, LocalTime.of(7, 0), LocalTime.of(7, 50)),
                                new PeriodTimeDTO(2, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                                new PeriodTimeDTO(3, LocalTime.of(9, 0), LocalTime.of(9, 50))))
                        .build()).build();
    }

    private static List<Room> rooms() {
        return List.of(Room.builder().schoolCode("PTIT").roomCode("101").capacity(60)
                .roomType("THEORY").isLab(false).build());
    }

    private static MockMultipartFile planWorkbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("KeHoachMoLop");
            String[] headers = {"Mã môn", "Tên học phần", "Loại lớp", "Sĩ số", "Khóa", "Số lớp mở",
                    "Tuần bắt đầu", "Tuần kết thúc", "Số tín chỉ", "Số tiết"};
            var header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);
            for (int i = 1; i <= 2; i++) {
                var row = sheet.createRow(i);
                String[] cells = {i == 1 ? "A_MISSING" : "Z_ASSIGNED", i == 1 ? "Missing course" : "Assigned course",
                        "LT", "30", "2024", "1", "10/08/2026", "10/08/2026", "3", "3"};
                for (int c = 0; c < cells.length; c++) row.createCell(c).setCellValue(cells[c]);
            }
            workbook.write(output);
            return new MockMultipartFile("planFile", "plan.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
