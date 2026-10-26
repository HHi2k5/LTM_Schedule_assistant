package com.service;

import com.dto.*;
import com.repository.RoomRepository;
import com.model.Room;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelScheduleServiceTest {
    private final AutoScheduleService automatic = mock(AutoScheduleService.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ExcelScheduleService excel = new ExcelScheduleService(mock(ScheduleService.class), automatic, rooms);

    @Test void threeFilePlanRetainsCreditsAndUsesAvailableRooms() throws Exception {
        Room room = new Room(); room.setRoomCode("101-A2"); room.setCapacity(60);
        when(rooms.findAllByOrderByCapacityAsc()).thenReturn(List.of(room));
        var plan = file(new Object[][]{{1, "INT1313", "Networking", 50, "2024", 1, 1, 1, 3, "x", "", "", "", "", "", 3}},
                new String[]{"STT", "Mã môn", "Tên học phần", "Sĩ số", "Khóa", "Số lớp mở", "Tuần bắt đầu", "Tiết BĐ", "Số tiết",
                        "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Số tín chỉ"});
        excel.autoScheduleFrom3Files(plan, null, null, "PTIT", "School", LocalDate.of(2026, 8, 10));
        var capture = ArgumentCaptor.forClass(SchoolWideScheduleRequest.class);
        verify(automatic).generateScheduleWithRooms(capture.capture(), eq(List.of(room)));
        var course = capture.getValue().getCourses().getFirst();
        assertEquals("INT1313", course.getCourseCode()); assertEquals(3, course.getCredits());
        assertEquals(2, course.getFixedDayOfWeek()); assertEquals(1, course.getFixedStartPeriod());
    }

    @Test void acceptsLegacyThreeColumnsAndRetainsOptionalCourseMetadata() throws Exception {
        excel.autoScheduleFromExcel(file(new Object[][]{{"Networking", "GV01 - Teacher One", 1, "INT1313", 3},
                {"Databases", "GV02 - Teacher Two", 1}}), "PTIT", "School", LocalDate.of(2026, 8, 10));
        var capture = ArgumentCaptor.forClass(SchoolWideScheduleRequest.class);
        verify(automatic).generateSchoolWideSchedule(capture.capture());
        var courses = capture.getValue().getCourses();
        assertEquals("INT1313", courses.get(0).getCourseCode()); assertEquals(3, courses.get(0).getCredits());
        assertEquals("GV01", courses.get(0).getTeachers().getFirst().getTeacherId());
        assertNull(courses.get(1).getCredits()); assertEquals("MH001", courses.get(1).getCourseCode());
    }

    @Test void rejectsInconsistentMetadataAndDuplicateAssignments() throws Exception {
        var inconsistent = file(new Object[][]{{"Networking", "GV01 - One", 1, "INT1313", 3},
                {"Networking", "GV02 - Two", 1, "INT1313", 4}});
        assertThrows(IllegalArgumentException.class, () -> excel.autoScheduleFromExcel(inconsistent, "PTIT", "School", null));
        var duplicate = file(new Object[][]{{"Networking", "GV01 - One", 1}, {"Networking", "GV01 - One", 2}});
        assertThrows(IllegalArgumentException.class, () -> excel.autoScheduleFromExcel(duplicate, "PTIT", "School", null));
        verifyNoInteractions(automatic);
    }

    private MockMultipartFile file(Object[][] rows) throws Exception {
        return file(rows, new String[0]);
    }

    private MockMultipartFile file(Object[][] rows, String[] headers) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("PhanCong"); var header = sheet.createRow(0);
            for (int j = 0; j < headers.length; j++) header.createCell(j).setCellValue(headers[j]);
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    var cell = row.createCell(j); var value = rows[i][j];
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(value.toString());
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "assignment.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
