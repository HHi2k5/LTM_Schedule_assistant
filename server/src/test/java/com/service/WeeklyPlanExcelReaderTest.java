package com.service;

import com.dto.*;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyPlanExcelReaderTest {
    static final Object[] HEADERS = {"STT", "Mã môn", "Tên học phần", "Sĩ số", "Khóa", "Số lớp mở",
            "Tuần bắt đầu — từ ngày", "Tuần kết thúc — đến ngày", "Số tín chỉ", "Tổng tiết LT", "Tổng tiết TH"};
    static final Object[] TEACHERS = {"Mã môn", "Tên học phần", "Khóa", "Mã GV", "Họ tên giảng viên", "Trọng số LT", "Trọng số TH"};
    static Object[] row() { return new Object[]{1, "NET", "Networking", 50, "2024", 1, "10/08/2026", "29/11/2026", 3, 30, 45}; }
    static MockMultipartFile plan() throws Exception { return file(new Object[][]{HEADERS, row()}); }
    static MockMultipartFile teachers() throws Exception { return file(new Object[][]{TEACHERS, {"NET", "Networking", "2024", "A", "A", 2, 8}, {"NET", "Networking", "2024", "B", "B", 8, 2}}); }
    static MockMultipartFile file(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            for (int r = 0; r < rows.length; r++) for (int c = 0; c < rows[r].length; c++) {
                var row = sheet.getRow(r); if (row == null) row = sheet.createRow(r);
                var cell = row.createCell(c); Object value = rows[r][c];
                if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                else if (value instanceof LocalDate date) {
                    cell.setCellValue(date); var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy")); cell.setCellStyle(style);
                } else cell.setCellValue(value == null ? "" : value.toString());
            }
            workbook.write(out); return new MockMultipartFile("file", "data.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
    @Test void readsSyllabusHoursAndAllWeightedCandidatesWithoutPreassigningATeacher() throws Exception {
        var course = new WeeklyPlanExcelReader().read(plan(), teachers()).getFirst();
        assertEquals(3, course.getCredits()); assertEquals(30, course.getTheoryPeriods()); assertEquals(45, course.getPracticePeriods());
        assertEquals(LocalDate.of(2026, 8, 10), course.getStudyStartDate()); assertEquals(LocalDate.of(2026, 11, 29), course.getStudyEndDate());
        assertEquals(2, course.getTeachers().size()); assertEquals(8, course.getTeachers().getFirst().getPracticeWeight());
        assertNull(course.getTeachers().getFirst().getAssignedClasses()); assertNull(course.getFixedDayOfWeek());
    }
    @Test void readsRealExcelDatesAndRejectsInvalidDatesAndMissingHoursOrWeights() throws Exception {
        var data = row(); data[6] = LocalDate.of(2026, 8, 10);
        assertEquals(LocalDate.of(2026, 8, 10), new WeeklyPlanExcelReader().read(file(new Object[][]{HEADERS, data}), teachers()).getFirst().getStudyStartDate());
        for (Object invalid : new Object[]{"31/02/2026", "10/08/26", ""}) {
            var bad = row(); bad[6] = invalid;
            assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(file(new Object[][]{HEADERS, bad}), teachers()));
        }
        for (Object invalid : new Object[]{"", -1, 1.5}) {
            var bad = row(); bad[10] = invalid;
            assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(file(new Object[][]{HEADERS, bad}), teachers()));
        }
        assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(plan(), null));
        var missing = file(new Object[][]{TEACHERS, {"NET", "Networking", "2024", "A", "A", "", 8}});
        assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(plan(), missing));
    }
    @Test void rejectsConflictingWeightsAndNeverUsesAnotherCohortsTeachers() throws Exception {
        var duplicate = file(new Object[][]{TEACHERS, {"NET", "Networking", "2024", "A", "A", 2, 8}, {"NET", "Networking", "2024", "A", "A", 8, 2}});
        assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(plan(), duplicate));
        var other = file(new Object[][]{TEACHERS, {"NET", "Networking", "2023", "A", "A", 2, 8}});
        assertThrows(IllegalArgumentException.class, () -> new WeeklyPlanExcelReader().read(plan(), other));
    }
}
