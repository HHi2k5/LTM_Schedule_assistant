package com.service;

import com.dto.*;
import com.model.Room;
import com.model.Teacher;
import com.repository.TeacherRepository;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin chỉ xếp lớp vào phòng: mọi lớp được lưu ở trạng thái chưa phân công giảng viên. */
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
    void excelPlanIsScheduledIntoRoomsWithoutAnyTeacher() throws Exception {
        var courses = new WeeklyPlanExcelReader().read(planWorkbook());
        assertEquals(2, courses.size());
        assertTrue(courses.stream().allMatch(course -> course.getTeachers().isEmpty()));

        var request = request(courses, 6);
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request, rooms());
        assertTrue(result.isSaved());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(2, result.getTotalClassesScheduled());
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> slot.getTeacherId() == null));
        assertTrue(result.getClassWorkloads().stream().allMatch(row -> row.getTeacherId() == null));
        assertTrue(result.getTeacherAllocations().isEmpty());
        verify(schedules).replaceWeeklyGenerated(eq(request), argThat(map -> map.size() == 1
                && map.get(Teacher.UNASSIGNED_ID).size() == 2), anyMap());
    }

    @Test
    void anIncompleteClassDoesNotHoldTheRoomForTheFollowingClass() {
        var request = request(List.of(course("A_TOO_LARGE", 30), course("Z_FITS", 3)), 3);
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request, rooms());
        assertEquals("PARTIAL_SUCCESS", result.getStatus());
        assertEquals(1, result.getTotalClassesScheduled());
        assertTrue(result.getScheduledClasses().stream().allMatch(s -> s.getCourseCode().equals("Z_FITS")));
        var missing = result.getUnscheduledClasses().getFirst();
        assertEquals("NO_AVAILABLE_SLOT", missing.getReasonCode());
        assertEquals(0, missing.getScheduledPeriods());
        assertEquals(30, missing.getMissingPeriods());
        assertNull(missing.getTeacherId());
        verify(schedules).replaceWeeklyGenerated(eq(request), anyMap(), anyMap());
    }

    @Test
    void whenNothingFitsAReportIsReturnedAndExistingSchedulesAreKept() {
        var tooLong = course("A_TOO_LARGE", 30);
        tooLong.setTotalClassesToOpen(2);
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(tooLong), 3), rooms());
        assertFalse(result.isSaved());
        assertEquals("NOT_SAVED", result.getStatus());
        assertEquals(2, result.getUnscheduledClasses().size());
        assertTrue(result.getScheduledClasses().isEmpty());
        verifyNoInteractions(schedules);
    }

    @Test
    void fullyScheduledResultRemainsSuccess() {
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(course("FITS", 3)), 3), rooms());
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.isSaved());
        assertTrue(result.getUnscheduledClasses().isEmpty());
        assertTrue(result.getUnscheduledWarnings().isEmpty());
    }

    @Test
    void missingRoomIsReported() {
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(course("NO_ROOM", 3)), 3), List.of());
        assertEquals("NO_ROOM", result.getUnscheduledClasses().getFirst().getReasonCode());
        assertFalse(result.isSaved());
        verifyNoInteractions(schedules);
    }

    @Test
    void aSubjectThatRequiresARoomTypeOnlyUsesRoomsOfThatType() {
        var projector = course("NEEDS_PROJECTOR", 3);
        projector.setTheoryRoomType("MAY_CHIEU");
        var rooms = new ArrayList<>(rooms());
        rooms.add(Room.builder().schoolCode("PTIT").roomCode("202").capacity(80).roomType("MAY_CHIEU").isLab(false).build());

        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(projector), 3), rooms);
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> slot.getRoom().equals("202")));

        var missing = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(projector), 3), rooms());
        assertEquals("NO_ROOM", missing.getUnscheduledClasses().getFirst().getReasonCode());
        assertTrue(missing.getUnscheduledClasses().getFirst().getReason().contains("MAY_CHIEU"));
    }

    @Test
    void practiceGroupsAreSizedByTheRoomsTheSubjectMayUse() {
        var lab = CourseDemandDTO.builder().stt("1").courseCode("LAB").courseName("Lab").cohort("2024").credits(3)
                .theoryPeriods(0).practicePeriods(3).totalClassesToOpen(1).classSize(40)
                .studyStartDate(START).studyEndDate(START.plusDays(5)).practiceRoomType("PHONG_MAY").build();
        var rooms = List.of(
                Room.builder().schoolCode("PTIT").roomCode("TH-BIG").capacity(60).roomType("THUC_HANH").isLab(true).build(),
                Room.builder().schoolCode("PTIT").roomCode("PM-01").capacity(20).roomType("PHONG_MAY").isLab(true).build());
        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(lab), 6), rooms);
        assertEquals("SUCCESS", result.getStatus());
        // 40 sinh viên, phòng máy chỉ 20 chỗ -> 2 tổ, đều ở phòng máy chứ không dùng phòng thực hành lớn hơn.
        assertEquals(2, result.getTotalClassesScheduled());
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> slot.getRoom().equals("PM-01")));
    }

    @Test
    void classesOfTheSameCourseAvoidOverlappingSoFewerTeachersCanCoverThem() {
        var twoClasses = course("SAME", 3);
        twoClasses.setTotalClassesToOpen(2);
        var rooms = new ArrayList<>(rooms());
        rooms.add(Room.builder().schoolCode("PTIT").roomCode("102").capacity(60).roomType("LY_THUYET").isLab(false).build());

        var result = new WeeklyScheduleEngine(teachers, schedules).generate(request(List.of(twoClasses), 6), rooms);
        assertEquals("SUCCESS", result.getStatus());
        var slots = result.getScheduledClasses();
        assertEquals(2, slots.size());
        var first = slots.get(0);
        var second = slots.get(1);
        boolean overlap = first.getDayOfWeek() == second.getDayOfWeek()
                && first.getStartPeriod() < second.getStartPeriod() + second.getNumPeriods()
                && second.getStartPeriod() < first.getStartPeriod() + first.getNumPeriods();
        assertFalse(overlap, "Hai lớp cùng môn không nên trùng giờ khi còn khung giờ trống.");
    }

    private static CourseDemandDTO course(String code, int required) {
        return CourseDemandDTO.builder().stt("1").courseCode(code).courseName(code).cohort("2024")
                .credits(3).theoryPeriods(required).practicePeriods(0).totalClassesToOpen(1).classSize(30)
                .studyStartDate(START).studyEndDate(START).build();
    }

    private static SchoolWideScheduleRequest request(List<CourseDemandDTO> courses, int periodCount) {
        var periods = new ArrayList<PeriodTimeDTO>();
        for (int period = 1; period <= periodCount; period++) {
            periods.add(new PeriodTimeDTO(period, LocalTime.of(6 + period, 0), LocalTime.of(6 + period, 50)));
        }
        return SchoolWideScheduleRequest.builder().semesterStartDate(START).totalWeeks(1).courses(courses)
                .targetSchool(SchoolConfigDTO.builder().schoolId("PTIT").schoolName("School").periods(periods).build()).build();
    }

    private static List<Room> rooms() {
        return List.of(Room.builder().schoolCode("PTIT").roomCode("101").capacity(60)
                .roomType("LY_THUYET").isLab(false).build());
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
                String[] cells = {i == 1 ? "A_FIRST" : "Z_SECOND", i == 1 ? "First course" : "Second course",
                        "LT", "30", "2024", "1", "10/08/2026", "10/08/2026", "3", "3"};
                for (int c = 0; c < cells.length; c++) row.createCell(c).setCellValue(cells[c]);
            }
            workbook.write(output);
            return new MockMultipartFile("planFile", "plan.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
