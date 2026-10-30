package com.service;

import com.dto.*;
import com.repository.RoomRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelValidationTest {
    final ScheduleService schedules = mock(ScheduleService.class);
    final ExcelScheduleService excel = new ExcelScheduleService(schedules, mock(AutoScheduleService.class), mock(RoomRepository.class), mock(ScheduleWriteLock.class));

    @Test void customPeriodTimesAreRetained() throws Exception {
        var capture = org.mockito.ArgumentCaptor.forClass(SchoolUploadRequest.class);
        importFile(personal("1", "1", true));
        verify(schedules).saveOrUpdateSchoolSchedule(capture.capture());
        assertEquals(LocalTime.of(7, 20), capture.getValue().getSchool().getPeriods().getFirst().getStart());
    }
    @ParameterizedTest @ValueSource(strings = {"-2", "1.5", "abc", "0", "31"})
    void malformedOrMissingPeriodReferencesCannotReachSave(String count) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> importFile(personal(count, "1", true)));
        verifyNoInteractions(schedules);
    }
    @ParameterizedTest @ValueSource(strings = {"", "---", "bad!", "31/02/26 đến 10/03/26", "17/08/26 đến 10/08/26"})
    void invalidWeeksOrDatesCannotReachSave(String weeks) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> importFile(personal("1", weeks, true)));
        verifyNoInteractions(schedules);
    }
    @Test void missingPeriodSheetAndEmptyFileCannotEraseSchedule() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> importFile(personal("1", "1", false)));
        assertThrows(IllegalArgumentException.class, () -> importFile(workbook(new Object[0][], true)));
        verifyNoInteractions(schedules);
    }
    @Test void dateRangesKeepOnlyActualClassDaysAndStrictMondayAnchor() throws Exception {
        var capture = org.mockito.ArgumentCaptor.forClass(SchoolUploadRequest.class);
        importFile(personal("1", "11/08/26 đến 24/08/26", true));
        verify(schedules).saveOrUpdateSchoolSchedule(capture.capture());
        assertEquals("-11", capture.getValue().getCourses().getFirst().getWeekSchedule());
        assertThrows(IllegalArgumentException.class, () -> excel.importTeacherScheduleFromExcel(personal("1", "1", true),
                "GV01", "Teacher", "A", "A", null, LocalDate.of(2026, 8, 11)));
    }
    @Test void isolatedPracticeDatesRemainTwoSeparateLessons() throws Exception {
        var capture = org.mockito.ArgumentCaptor.forClass(SchoolUploadRequest.class);
        importFile(workbook(new Object[][]{
                {"NET", "Networking", "Lớp 01 - Tổ 1", 3, 2, 1, 1, "LAB", "17/08/26 đến 17/08/26", "GV01", "Teacher"},
                {"NET", "Networking", "Lớp 01 - Tổ 1", 3, 2, 1, 1, "LAB", "31/08/26 đến 31/08/26", "GV01", "Teacher"}
        }, true));
        verify(schedules).saveOrUpdateSchoolSchedule(capture.capture());
        assertEquals(List.of("-1", "---1"), capture.getValue().getCourses().stream().map(TeachingCourseDTO::getWeekSchedule).toList());
    }
    @Test void outerExcelTransactionRollsBackCheckedExceptions() throws Exception {
        var method = ExcelScheduleService.class.getMethod("autoScheduleFrom3Files", MultipartFile.class, MultipartFile.class,
                MultipartFile.class, String.class, String.class, LocalDate.class);
        var attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(method, ExcelScheduleService.class);
        assertTrue(attribute.rollbackOn(new IOException("read failure")));
    }
    void importFile(MockMultipartFile file) throws Exception {
        excel.importTeacherScheduleFromExcel(file, "GV01", "Teacher", "A", "A", null, BackendRegressionTest.MONDAY);
    }
    static MockMultipartFile personal(String count, String weeks, boolean periods) throws Exception {
        return workbook(new Object[][]{{"NET", "Networking", "01", 3, 2, 1, count, "101", weeks}}, periods);
    }
    static MockMultipartFile workbook(Object[][] rows, boolean periods) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("MonHoc"); var header = sheet.createRow(0);
            if (rows.length > 0 && rows[0].length > 9) {
                header.createCell(9).setCellValue("Mã GV");
                header.createCell(10).setCellValue("Tên GV");
            }
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int c = 0; c < rows[i].length; c++) row.createCell(c).setCellValue(rows[i][c].toString());
            }
            if (periods) {
                var sheet2 = book.createSheet("TietHoc"); sheet2.createRow(0);
                var row = sheet2.createRow(1); row.createCell(0).setCellValue(1);
                row.createCell(1).setCellValue("07:20"); row.createCell(2).setCellValue("08:10");
            }
            book.write(out);
            return new MockMultipartFile("file", "schedule.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
