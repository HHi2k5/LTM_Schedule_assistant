package com.service;

import com.dto.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExcelScheduleService {

    private final ScheduleService scheduleService;
    private final AutoScheduleService autoScheduleService;

    // Regex bắt định dạng ngày: dd/MM/yy hoặc dd/MM/yyyy
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})");

    /**
     * 1. Đọc file Excel lịch giảng viên theo mẫu 9 cột:
     * [0] Mã MH | [1] Tên môn học | [2] Nhóm tổ | [3] Số tín chỉ | [4] Thứ | [5] Tiết BĐ | [6] Số tiết | [7] Phòng | [8] Thời gian học
     */
    @Transactional
    public void importTeacherScheduleFromExcel(
            MultipartFile file,
            String teacherId,
            String teacherName,
            String schoolId,
            String schoolName,
            String colorCode,
            LocalDate semesterStartDate) throws Exception {

        List<TeachingCourseDTO> courses = new ArrayList<>();
        LocalDate resolvedSemesterStart = semesterStartDate;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            if (resolvedSemesterStart == null) {
                resolvedSemesterStart = detectEarliestMonday(sheet, formatter);
            }

            String lastCourseCode = "";
            String lastCourseName = "";
            String lastGroup = "01";

            // Duyệt từ dòng 1 (bỏ qua tiêu đề)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Đọc các cột quan trọng
                String courseCodeCell = getCellText(row, 0, formatter);
                String courseNameCell = getCellText(row, 1, formatter);
                String groupCell = getCellText(row, 2, formatter);
                String dayStr = getCellText(row, 4, formatter);
                String startPeriodStr = getCellText(row, 5, formatter);

                // Nếu dòng trống hoàn toàn hoặc không có thông tin tiết học thì bỏ qua
                if (dayStr.isBlank() || startPeriodStr.isBlank()) {
                    // Nếu dòng này trống nhưng có tên môn thì có thể là dòng ngắt, reset tạm hoặc tiếp tục
                    continue;
                }

                if (!courseCodeCell.isBlank()) lastCourseCode = courseCodeCell;
                if (!courseNameCell.isBlank()) lastCourseName = courseNameCell;
                if (!groupCell.isBlank()) lastGroup = groupCell;

                if (lastCourseName.isBlank()) continue;

                int dayOfWeek = parseDayOfWeek(dayStr);
                int startPeriod = parseIntSafe(startPeriodStr, 1);
                int numPeriods = parseIntSafe(getCellText(row, 6, formatter), 2);
                String room = getCellText(row, 7, formatter);
                String studyTimeRange = getCellText(row, 8, formatter);

                // Chuyển khoảng thời gian thành chuỗi tuần chuẩn (16 tuần)
                String weekSchedule = convertDateRangeToWeekString(studyTimeRange, resolvedSemesterStart, 16);

                String displayCourseName = lastCourseName + " (Nhóm " + lastGroup + ")";

                courses.add(TeachingCourseDTO.builder()
                        .courseCode(!lastCourseCode.isBlank() ? lastCourseCode : "MH_AUTO")
                        .courseName(displayCourseName)
                        .room(room)
                        .dayOfWeek(dayOfWeek)
                        .startPeriod(startPeriod)
                        .numPeriods(numPeriods)
                        .weekSchedule(weekSchedule)
                        .build());
            }
        }

        SchoolConfigDTO schoolConfig = SchoolConfigDTO.builder()
                .schoolId(schoolId)
                .schoolName(schoolName)
                .colorCode(colorCode != null ? colorCode : "#2563eb")
                .periods(getDefault12Periods())
                .build();

        SchoolUploadRequest uploadRequest = SchoolUploadRequest.builder()
                .teacherId(teacherId)
                .teacherName(teacherName)
                .semesterStartDate(resolvedSemesterStart)
                .school(schoolConfig)
                .courses(courses)
                .build();

        scheduleService.saveOrUpdateSchoolSchedule(uploadRequest);
    }

    @Transactional
    public AutoScheduleResponse autoScheduleFromExcel(
            MultipartFile file,
            String schoolId,
            String schoolName,
            LocalDate semesterStartDate) throws Exception {

        Map<String, List<TeacherCandidateDTO>> courseTeachersMap = new LinkedHashMap<>();
        Map<String, Integer> courseTotalClassesMap = new HashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String courseRaw = getCellText(row, 0, formatter);
                String teacherRaw = getCellText(row, 1, formatter);
                int numClasses = parseIntSafe(getCellText(row, 2, formatter), 4);

                if (courseRaw.isBlank() || teacherRaw.isBlank()) continue;

                String teacherId = extractTeacherId(teacherRaw);
                String teacherName = extractTeacherName(teacherRaw);

                courseTeachersMap.putIfAbsent(courseRaw, new ArrayList<>());
                courseTeachersMap.get(courseRaw).add(TeacherCandidateDTO.builder()
                        .teacherId(teacherId)
                        .teacherName(teacherName)
                        .assignedClasses(numClasses)
                        .build());

                courseTotalClassesMap.put(courseRaw,
                        courseTotalClassesMap.getOrDefault(courseRaw, 0) + numClasses);
            }
        }

        List<CourseDemandDTO> courseDemands = new ArrayList<>();
        int codeIndex = 1;
        for (Map.Entry<String, List<TeacherCandidateDTO>> entry : courseTeachersMap.entrySet()) {
            String courseName = entry.getKey();
            courseDemands.add(CourseDemandDTO.builder()
                    .courseCode(String.format("MH%03d", codeIndex++))
                    .courseName(courseName)
                    .totalClassesToOpen(courseTotalClassesMap.get(courseName))
                    .teachers(entry.getValue())
                    .build());
        }

        SchoolConfigDTO targetSchool = SchoolConfigDTO.builder()
                .schoolId(schoolId != null ? schoolId : "PTIT")
                .schoolName(schoolName != null ? schoolName : "Học viện Công nghệ Bưu chính Viễn thông")
                .colorCode("#dc2626")
                .periods(getDefault12Periods())
                .build();

        SchoolWideScheduleRequest request = SchoolWideScheduleRequest.builder()
                .semesterStartDate(semesterStartDate != null ? semesterStartDate : LocalDate.of(2026, 8, 10))
                .totalWeeks(16)
                .buildingSuffix("A2")
                .targetSchool(targetSchool)
                .courses(courseDemands)
                .build();

        return autoScheduleService.generateSchoolWideSchedule(request);
    }

    /**
     * Chuyển khoảng thời gian thành chuỗi tuần an toàn (Mặc định 16 tuần chuẩn)
     */
    private String convertDateRangeToWeekString(String rangeText, LocalDate semesterMonday, int maxWeeks) {
        if (rangeText == null || rangeText.isBlank()) {
            return "1111111111111111"; // Học tất cả các tuần nếu trống
        }
        if (!rangeText.contains("/")) {
            return rangeText.trim();
        }

        List<LocalDate> dates = extractDates(rangeText);
        if (dates.size() < 2) {
            return "1111111111111111";
        }

        LocalDate startDate = dates.get(0);
        LocalDate endDate = dates.get(1);

        int startWeek = (int) ChronoUnit.WEEKS.between(semesterMonday, startDate) + 1;
        int endWeek = (int) ChronoUnit.WEEKS.between(semesterMonday, endDate) + 1;

        startWeek = Math.max(1, startWeek);
        endWeek = Math.min(maxWeeks, Math.max(startWeek, endWeek));

        StringBuilder sb = new StringBuilder();
        for (int w = 1; w <= maxWeeks; w++) {
            if (w >= startWeek && w <= endWeek) {
                sb.append('1'); // Đánh dấu tuần có học
            } else {
                sb.append('-'); // Không học
            }
        }
        return sb.toString();
    }

    private LocalDate detectEarliestMonday(Sheet sheet, DataFormatter formatter) {
        LocalDate minDate = null;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String timeText = getCellText(row, 8, formatter);
            List<LocalDate> dates = extractDates(timeText);
            for (LocalDate d : dates) {
                if (minDate == null || d.isBefore(minDate)) {
                    minDate = d;
                }
            }
        }
        if (minDate != null) {
            return minDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return LocalDate.of(2026, 8, 10);
    }

    private List<LocalDate> extractDates(String text) {
        List<LocalDate> result = new ArrayList<>();
        if (text == null) return result;
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String dateStr = matcher.group(1);
            LocalDate parsed = parseFlexibleDate(dateStr);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        String[] parts = dateStr.split("/");
        if (parts.length != 3) return null;
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            if (year < 100) {
                year += 2000;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private int parseDayOfWeek(String dayStr) {
        String s = dayStr.trim().toUpperCase();
        if (s.equals("CN") || s.contains("CHỦ NHẬT") || s.equals("8")) return 8;
        return parseIntSafe(s.replaceAll("[^0-9]", ""), 2);
    }

    private String getCellText(Row row, int cellIndex, DataFormatter formatter) {
        Cell cell = row.getCell(cellIndex);
        return (cell == null) ? "" : formatter.formatCellValue(cell).trim();
    }

    private int parseIntSafe(String val, int defaultVal) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private String extractTeacherId(String raw) {
        if (raw.contains("-")) return raw.split("-")[0].trim();
        return "GV_" + raw.toUpperCase().replaceAll("\\s+", "");
    }

    private String extractTeacherName(String raw) {
        if (raw.contains("-")) return raw.split("-", 2)[1].trim();
        return raw.trim();
    }

    private List<PeriodTimeDTO> getDefault12Periods() {
        return List.of(
                new PeriodTimeDTO(1, LocalTime.of(7, 0), LocalTime.of(7, 50)),
                new PeriodTimeDTO(2, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                new PeriodTimeDTO(3, LocalTime.of(9, 0), LocalTime.of(9, 50)),
                new PeriodTimeDTO(4, LocalTime.of(10, 0), LocalTime.of(10, 50)),
                new PeriodTimeDTO(5, LocalTime.of(11, 0), LocalTime.of(11, 50)),
                new PeriodTimeDTO(6, LocalTime.of(13, 0), LocalTime.of(13, 50)),
                new PeriodTimeDTO(7, LocalTime.of(14, 0), LocalTime.of(14, 50)),
                new PeriodTimeDTO(8, LocalTime.of(15, 0), LocalTime.of(15, 50)),
                new PeriodTimeDTO(9, LocalTime.of(16, 0), LocalTime.of(16, 50)),
                new PeriodTimeDTO(10, LocalTime.of(17, 0), LocalTime.of(17, 50)),
                new PeriodTimeDTO(11, LocalTime.of(18, 0), LocalTime.of(18, 50)),
                new PeriodTimeDTO(12, LocalTime.of(19, 0), LocalTime.of(19, 50))
        );
    }
}