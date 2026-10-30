package com.service;

import com.dto.*;
import com.model.Room;
import com.repository.RoomRepository;
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
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ExcelScheduleService {

    private final ScheduleService scheduleService;
    private final AutoScheduleService autoScheduleService;
    private final RoomRepository roomRepository;
    private final ScheduleWriteLock writeLock;

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})");

    /**
     * 1. Giảng viên import lịch cá nhân từ file Excel (Hỗ trợ Cột 9: Giờ bắt đầu, Cột 10: Giờ kết thúc)
     */
    @Transactional(rollbackFor = Exception.class)
    public void importTeacherScheduleFromExcel(
            MultipartFile file,
            String teacherId,
            String teacherName,
            String schoolId,
            String schoolName,
            String colorCode,
            LocalDate semesterStartDate) throws Exception {

        validateFile(file);
        List<TeachingCourseDTO> courses = new ArrayList<>();
        List<PeriodTimeDTO> importedPeriods;
        LocalDate resolvedSemesterStart = semesterStartDate;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet("MonHoc") != null ? workbook.getSheet("MonHoc") : workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            boolean exportedTeacherColumns = header != null
                    && "Mã GV".equalsIgnoreCase(getCellText(header, 9, formatter))
                    && "Tên GV".equalsIgnoreCase(getCellText(header, 10, formatter));
            
            // Khởi tạo bảng giờ linh hoạt có sẵn Tiết 6 (12:00 - 13:00)
            Map<Integer, PeriodTimeDTO> periodMap = new TreeMap<>();
            for (PeriodTimeDTO p : readPeriodsOptional(workbook, formatter)) {
                periodMap.put(p.getPeriod(), p);
            }

            if (resolvedSemesterStart == null) {
                resolvedSemesterStart = detectEarliestMonday(sheet, formatter);
            }
            ScheduleValidation.monday(resolvedSemesterStart);

            String lastCourseCode = "";
            String lastCourseName = "";
            String lastGroup = "01";

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                if (rowIsEmpty(row, formatter)) continue;
                try {
                    String courseCodeCell = getCellText(row, 0, formatter);
                    String courseNameCell = getCellText(row, 1, formatter);
                    String groupCell = getCellText(row, 2, formatter);
                    String dayStr = getCellText(row, 4, formatter);
                    String startPeriodStr = getCellText(row, 5, formatter);
                    
                    // Đọc thêm Cột 9 (Giờ bắt đầu) và Cột 10 (Giờ kết thúc)
                    LocalTime customStart = exportedTeacherColumns ? null : parseOptionalTimeCell(row.getCell(9), formatter);
                    LocalTime customEnd = exportedTeacherColumns ? null : parseOptionalTimeCell(row.getCell(10), formatter);

                    if (!courseCodeCell.isBlank()) lastCourseCode = courseCodeCell;
                    if (!courseNameCell.isBlank()) lastCourseName = courseNameCell;
                    if (!groupCell.isBlank()) lastGroup = groupCell;

                    if (lastCourseName.isBlank()) throw new IllegalArgumentException("Thiếu tên môn.");
                    if (dayStr.isBlank()) throw new IllegalArgumentException("Thiếu thứ.");

                    int dayOfWeek = parseDayOfWeek(dayStr);
                    
                    // LOGIC QUAN TRỌNG: Gán Tiết 6 tự động nếu giờ là 12:00 và tiết để trống
                    int startPeriod;
                    if (startPeriodStr.isBlank() && customStart != null && customStart.equals(LocalTime.of(12, 0))) {
                        startPeriod = 6;
                    } else if (!startPeriodStr.isBlank()) {
                        startPeriod = parseIntSafe(startPeriodStr, 1);
                    } else {
                        throw new IllegalArgumentException("Thiếu tiết bắt đầu.");
                    }

                    int numPeriods = parseIntSafe(getCellText(row, 6, formatter), 2);
                    String room = getCellText(row, 7, formatter);
                    String studyTimeRange = getCellText(row, 8, formatter);

                    // Tự động căn chỉnh bảng tiết học để khớp 100% với giờ thực tế trong file Excel
                    if (customStart != null && customEnd != null) {
                        if (!customEnd.isAfter(customStart)) {
                            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
                        }
                        applyCustomTimeToPeriods(periodMap, startPeriod, numPeriods, customStart, customEnd);
                    }

                    String weekSchedule = convertDateRangeToWeekString(studyTimeRange, resolvedSemesterStart, dayOfWeek);
                    String displayCourseName = lastCourseName + " (Nhóm " + lastGroup + ")";

                    courses.add(TeachingCourseDTO.builder()
                            .courseCode(!lastCourseCode.isBlank() ? lastCourseCode : "MH_AUTO")
                            .courseName(displayCourseName)
                            .room(room)
                            .dayOfWeek(dayOfWeek)
                            .startPeriod(startPeriod)
                            .numPeriods(numPeriods)
                            .weekSchedule(weekSchedule)
                            .customStartTime(customStart) // Ánh xạ giờ thực tế
                            .customEndTime(customEnd)     // Ánh xạ giờ kết thúc
                            .build());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("MonHoc dòng " + (i + 1) + ": " + e.getMessage());
                }
            }
            if (periodMap.isEmpty()) throw new IllegalArgumentException("Thiếu sheet TietHoc hoặc giờ thực tế để xác định bảng tiết.");
            // Chuẩn hóa lại bảng tiết tránh chồng lấn
            importedPeriods = reconstructNonOverlappingPeriods(periodMap);
        }

        SchoolConfigDTO schoolConfig = SchoolConfigDTO.builder()
                .schoolId(schoolId)
                .schoolName(schoolName)
                .colorCode(colorCode != null ? colorCode : "#2563eb")
                .periods(importedPeriods)
                .build();

        SchoolUploadRequest uploadRequest = SchoolUploadRequest.builder()
                .teacherId(teacherId)
                .teacherName(teacherName)
                .semesterStartDate(resolvedSemesterStart)
                .school(schoolConfig)
                .courses(courses)
                .build();

        ScheduleValidation.upload(uploadRequest);
        scheduleService.saveOrUpdateSchoolSchedule(uploadRequest);
    }

    /**
     * Wrapper giữ tương thích nếu nơi khác gọi hàm 1 file
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse autoScheduleFromExcel(
            MultipartFile file,
            String schoolId,
            String schoolName,
            LocalDate semesterStartDate) throws Exception {

        validateFile(file);
        LocalDate resolvedStart = semesterStartDate != null ? semesterStartDate : LocalDate.of(2026, 8, 10);
        if (resolvedStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Ngày bắt đầu tuần 1 phải là Thứ Hai.");
        }
        Map<String, CourseDemandDTO> demands = new LinkedHashMap<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String courseRaw = getCellText(row, 0, formatter);
                String teacherRaw = getCellText(row, 1, formatter);
                String classesText = getCellText(row, 2, formatter);
                int numClasses = classesText.isBlank() ? 4 : parseIntSafe(classesText, -1);

                if (rowIsEmpty(row, formatter)) continue;
                if (courseRaw.isBlank() || teacherRaw.isBlank()) throw new IllegalArgumentException("Dòng " + (i + 1) + ": thiếu môn học hoặc giảng viên.");
                if (numClasses < 1 || numClasses > 8) throw new IllegalArgumentException("Dòng " + (i + 1) + ": số lớp phải từ 1 đến 8.");
                String courseCode = getCellText(row, 3, formatter);
                String creditsText = getCellText(row, 4, formatter);
                Integer credits = creditsText.isBlank() ? null : parseIntSafe(creditsText, -1);
                if (credits != null && (credits < 1 || credits > 30)) throw new IllegalArgumentException("Dòng " + (i + 1) + ": số tín chỉ phải từ 1 đến 30.");

                String teacherId = extractTeacherId(teacherRaw);
                String teacherName = extractTeacherName(teacherRaw);

                String key = courseCode.isBlank() ? "name:" + courseRaw : "code:" + courseCode;
                CourseDemandDTO demand = demands.computeIfAbsent(key, ignored -> CourseDemandDTO.builder()
                        .courseCode(courseCode).courseName(courseRaw).credits(credits)
                        .teachers(new ArrayList<>()).build());
                if (!courseRaw.equals(demand.getCourseName()) || (credits != null && demand.getCredits() != null && !credits.equals(demand.getCredits()))) {
                    throw new IllegalArgumentException("Dòng " + (i + 1) + ": thông tin môn học không thống nhất.");
                }
                if (demand.getCredits() == null) demand.setCredits(credits);
                if (demand.getTeachers().stream().anyMatch(t -> t.getTeacherId().equals(teacherId))) {
                    throw new IllegalArgumentException("Dòng " + (i + 1) + ": giảng viên bị lặp trong cùng môn học.");
                }
                demand.getTeachers().add(TeacherCandidateDTO.builder()
                        .teacherId(teacherId)
                        .teacherName(teacherName)
                        .assignedClasses(numClasses)
                        .build());

                demand.setTotalClassesToOpen(demand.getTotalClassesToOpen() + numClasses);
            }
        }

        List<CourseDemandDTO> courseDemands = new ArrayList<>(demands.values());
        if (courseDemands.isEmpty()) throw new IllegalArgumentException("File phân công chưa có môn học và giảng viên hợp lệ.");
        Set<String> usedCodes = new HashSet<>();
        courseDemands.forEach(d -> { if (!d.getCourseCode().isBlank()) usedCodes.add(d.getCourseCode()); });
        int codeIndex = 1;
        for (CourseDemandDTO demand : courseDemands) {
            if (demand.getCourseCode().isBlank()) {
                String generated;
                do { generated = String.format("MH%03d", codeIndex++); } while (usedCodes.contains(generated));
                demand.setCourseCode(generated);
                usedCodes.add(generated);
            }
        }

        SchoolConfigDTO targetSchool = SchoolConfigDTO.builder()
                .schoolId(schoolId != null ? schoolId : "PTIT")
                .schoolName(schoolName != null ? schoolName : "Học viện Công nghệ Bưu chính Viễn thông")
                .colorCode("#dc2626")
                .periods(getDefault12PeriodsWithNoonSlot6())
                .build();

        SchoolWideScheduleRequest request = SchoolWideScheduleRequest.builder()
                .semesterStartDate(resolvedStart)
                .totalWeeks(16)
                .buildingSuffix("A2")
                .targetSchool(targetSchool)
                .courses(courseDemands)
                .build();

        return autoScheduleService.generateSchoolWideSchedule(request);
    }

    /**
     * 2. Xử lý trọn bộ 3 file Excel
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse autoScheduleFrom3Files(
            MultipartFile planFile,
            MultipartFile teacherFile,
            MultipartFile roomFile,
            String schoolId,
            String schoolName,
            LocalDate semesterStartDate) throws Exception {
        validateFile(planFile);
        LocalDate resolvedStart = semesterStartDate != null ? semesterStartDate : LocalDate.of(2026, 8, 10);
        if (resolvedStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Ngày bắt đầu tuần 1 phải là Thứ Hai.");
        }

        String resolvedSchoolId = (schoolId != null && !schoolId.isBlank()) ? schoolId : "PTIT";

        List<CourseDemandDTO> courseDemands = new WeeklyPlanExcelReader().read(planFile, teacherFile);
        if (courseDemands.isEmpty()) throw new IllegalArgumentException("Kế hoạch không có dòng hợp lệ.");
        
        writeLock.acquire();
        List<Room> availableRooms = parseAndSaveRoomExcel(roomFile, resolvedSchoolId);

        SchoolConfigDTO targetSchool = SchoolConfigDTO.builder()
                .schoolId(resolvedSchoolId)
                .schoolName(schoolName != null ? schoolName : "Học viện Công nghệ Bưu chính Viễn thông")
                .colorCode("#dc2626")
                .periods(getDefault12PeriodsWithNoonSlot6())
                .build();

        SchoolWideScheduleRequest request = SchoolWideScheduleRequest.builder()
                .semesterStartDate(resolvedStart)
                .totalWeeks((int) java.time.temporal.ChronoUnit.DAYS.between(resolvedStart,
                        courseDemands.stream().map(CourseDemandDTO::getStudyEndDate).max(LocalDate::compareTo).orElseThrow()) / 7 + 1)
                .buildingSuffix("A2")
                .targetSchool(targetSchool)
                .courses(courseDemands)
                .build();

        var result = autoScheduleService.generateScheduleWithRooms(request, availableRooms);
        if (!result.isSaved()) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return result;
    }

    // ================= HELPER METHODS CĂN CHỈNH GIỜ =================

    private LocalTime parseOptionalTimeCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() >= 0 && cell.getNumericCellValue() < 1) {
            return LocalTime.ofSecondOfDay(Math.round(cell.getNumericCellValue() * 86400) % 86400);
        }
        String text = formatter.formatCellValue(cell).trim();
        if (text.isBlank()) return null;
        text = text.toLowerCase().replace("h", ":");
        String[] parts = text.split(":");
        if (parts.length >= 2) {
            return LocalTime.of(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyCustomTimeToPeriods(Map<Integer, PeriodTimeDTO> periodMap, int startPeriod, int numPeriods, LocalTime customStart, LocalTime customEnd) {
        int totalMinutes = (customEnd.toSecondOfDay() - customStart.toSecondOfDay()) / 60;
        int slotMinutes = Math.max(1, totalMinutes / numPeriods);
        for (int i = 0; i < numPeriods; i++) {
            int p = startPeriod + i;
            LocalTime pStart = customStart.plusMinutes((long) i * slotMinutes);
            LocalTime pEnd = (i == numPeriods - 1) ? customEnd : customStart.plusMinutes((long) (i + 1) * slotMinutes);
            periodMap.put(p, new PeriodTimeDTO(p, pStart, pEnd));
        }
    }

    private List<PeriodTimeDTO> reconstructNonOverlappingPeriods(Map<Integer, PeriodTimeDTO> periodMap) {
        int maxP = periodMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(12);
        List<PeriodTimeDTO> result = new ArrayList<>();
        LocalTime cursor = LocalTime.of(7, 0);

        for (int p = 1; p <= maxP; p++) {
            PeriodTimeDTO existing = periodMap.get(p);
            if (existing != null && !existing.getStart().isBefore(cursor)) {
                result.add(existing);
                cursor = existing.getEnd();
            } else if (existing != null) {
                int duration = Math.max(15, (existing.getEnd().toSecondOfDay() - existing.getStart().toSecondOfDay()) / 60);
                LocalTime s = cursor;
                LocalTime e = s.plusMinutes(duration);
                result.add(new PeriodTimeDTO(p, s, e));
                cursor = e;
            } else {
                LocalTime s = cursor;
                LocalTime e = s.plusMinutes(45);
                result.add(new PeriodTimeDTO(p, s, e));
                cursor = e;
            }
        }
        return result;
    }

    private List<PeriodTimeDTO> readPeriodsOptional(Workbook workbook, DataFormatter formatter) {
        Sheet sheet = workbook.getSheet("TietHoc");
        if (sheet == null) {
            return List.of();
        }
        var periods = new ArrayList<PeriodTimeDTO>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || rowIsEmpty(row, formatter)) continue;
            try {
                periods.add(new PeriodTimeDTO(parseIntSafe(getCellText(row, 0, formatter), 0),
                        periodTime(row.getCell(1), formatter), periodTime(row.getCell(2), formatter)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("TietHoc dòng " + (i + 1) + ": tiết/giờ không hợp lệ.");
            }
        }
        return periods.isEmpty() ? getDefault12PeriodsWithNoonSlot6() : periods;
    }

    /**
     * Cấu hình chuẩn mặc định (Khung 12h-13h gán là Tiết 6)
     */
    private List<PeriodTimeDTO> getDefault12PeriodsWithNoonSlot6() {
        return List.of(
                new PeriodTimeDTO(1, LocalTime.of(7, 0), LocalTime.of(7, 50)),
                new PeriodTimeDTO(2, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                new PeriodTimeDTO(3, LocalTime.of(9, 0), LocalTime.of(9, 50)),
                new PeriodTimeDTO(4, LocalTime.of(10, 0), LocalTime.of(10, 50)),
                new PeriodTimeDTO(5, LocalTime.of(11, 0), LocalTime.of(11, 50)),
                new PeriodTimeDTO(6, LocalTime.of(12, 0), LocalTime.of(13, 0)), // Tiết 6 đặc biệt
                new PeriodTimeDTO(7, LocalTime.of(13, 0), LocalTime.of(13, 50)),
                new PeriodTimeDTO(8, LocalTime.of(14, 0), LocalTime.of(14, 50)),
                new PeriodTimeDTO(9, LocalTime.of(15, 0), LocalTime.of(15, 50)),
                new PeriodTimeDTO(10, LocalTime.of(16, 0), LocalTime.of(16, 50)),
                new PeriodTimeDTO(11, LocalTime.of(17, 0), LocalTime.of(17, 50)),
                new PeriodTimeDTO(12, LocalTime.of(18, 0), LocalTime.of(18, 50))
        );
    }

    // ==============================================================================

    private List<Room> parseAndSaveRoomExcel(MultipartFile roomFile, String schoolCode) throws Exception {
        if (roomFile != null && !roomFile.isEmpty()) {
            validateFile(roomFile);
            List<Room> importedRooms = new ArrayList<>();
            Set<String> roomCodes = new HashSet<>();
            try (InputStream is = roomFile.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter formatter = new DataFormatter();

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String roomCode = getCellText(row, 0, formatter);
                    String roomTypeRaw = getCellText(row, 1, formatter).toUpperCase();
                    String capacityStr = getCellText(row, 2, formatter);
                    String building = getCellText(row, 3, formatter);

                    if (rowIsEmpty(row, formatter)) continue;
                    if (roomCode.isBlank() || !roomCodes.add(roomCode.toUpperCase(Locale.ROOT)))
                        throw new IllegalArgumentException("Danh sách phòng dòng " + (i + 1) + ": mã phòng thiếu hoặc lặp.");

                    boolean isLab = roomTypeRaw.contains("THỰC HÀNH")
                            || roomTypeRaw.contains("LAB")
                            || roomTypeRaw.contains("PRACTICE")
                            || roomTypeRaw.contains("MÁY")
                            || roomCode.toUpperCase().contains("LAB");

                    int capacity = parseIntSafe(capacityStr, isLab ? 30 : 60);
                    ScheduleValidation.text(roomCode, 50, "Dòng " + (i + 1) + ": mã phòng");
                    if (capacity < 1 || capacity > 1000) throw new IllegalArgumentException("Dòng " + (i + 1) + ": sức chứa phòng không hợp lệ.");
                    String normalizedType;
                    if (isLab) {
                        normalizedType = "LAB_PRACTICE";
                    } else if (capacity >= 120) {
                        normalizedType = "THEORY_LARGE";
                    } else if (capacity >= 80) {
                        normalizedType = "THEORY_MEDIUM";
                    } else {
                        normalizedType = "THEORY_STANDARD";
                    }

                    Room room = roomRepository.findBySchoolCodeAndRoomCodeIgnoreCase(schoolCode, roomCode)
                            .orElse(new Room());
                    room.setRoomCode(roomCode);
                    room.setRoomName((isLab ? "Phòng LAB " : "Phòng LT ") + roomCode);
                    room.setRoomType(normalizedType);
                    room.setCapacity(capacity);
                    room.setLab(isLab);
                    room.setBuilding(building.isBlank() ? "A2" : building);
                    room.setSchoolCode(schoolCode);

                    importedRooms.add(roomRepository.save(room));
                }
            }
            if (!importedRooms.isEmpty()) {
                importedRooms.sort(Comparator.comparingInt(Room::getCapacity));
                return importedRooms;
            }
        }

        List<Room> dbRooms = roomRepository.findBySchoolCodeOrderByCapacityAsc(schoolCode);
        if (!dbRooms.isEmpty()) {
            return dbRooms;
        }

        throw new IllegalArgumentException("Trường chưa có danh mục phòng; cần tải file phòng học.");
    }

    private String convertDateRangeToWeekString(String value, LocalDate anchor, int day) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing study weeks/date range.");
        if (!value.contains("/")) {
            if (!value.matches("[a-zA-Z0-9 -]{1,100}") || value.replaceAll("[- ]", "").isEmpty())
                throw new IllegalArgumentException("Invalid study week string.");
            return value;
        }
        var dates = extractDates(value);
        if (dates.size() != 2 || dates.get(1).isBefore(dates.get(0))) throw new IllegalArgumentException("Invalid study date range.");
        var weeks = new StringBuilder();
        for (int w = 0; w < 100; w++) {
            var date = anchor.plusDays(w * 7L + day - 2);
            weeks.append(!date.isBefore(dates.get(0)) && !date.isAfter(dates.get(1)) ? '1' : '-');
        }
        if (dates.get(0).isBefore(anchor) || dates.get(1).isAfter(anchor.plusWeeks(100).minusDays(1)) || weeks.indexOf("1") < 0)
            throw new IllegalArgumentException("Study dates are outside the semester anchor/100 weeks or contain no class day.");
        return weeks.toString().replaceFirst("-+$", "");
    }

    private LocalDate detectEarliestMonday(Sheet sheet, DataFormatter formatter) {
        LocalDate minDate = null;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (LocalDate d : extractDates(getCellText(row, 8, formatter))) {
                if (minDate == null || d.isBefore(minDate)) minDate = d;
            }
        }
        return (minDate != null)
                ? minDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.of(2026, 8, 10);
    }

    private List<LocalDate> extractDates(String text) {
        List<LocalDate> result = new ArrayList<>();
        if (text == null) return result;
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            LocalDate parsed = parseFlexibleDate(matcher.group(1));
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
            if (year < 100) year += 2000;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid calendar date: " + dateStr);
        }
    }

    private int parseDayOfWeek(String dayStr) {
        String s = dayStr.trim().toUpperCase();
        if (s.equals("CN") || s.contains("CHỦ NHẬT") || s.equals("8")) return 8;
        if (!s.matches("(?:THỨ\\s*)?[2-7]")) throw new IllegalArgumentException("Thứ phải từ 2 đến 8 hoặc CN.");
        return Integer.parseInt(s.substring(s.length() - 1));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Cần file .xlsx không rỗng, tối đa 10 MB.");
        }
    }

    private boolean rowIsEmpty(Row row, DataFormatter formatter) {
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private LocalTime periodTime(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() >= 0 && cell.getNumericCellValue() < 1) {
            return LocalTime.ofSecondOfDay(Math.round(cell.getNumericCellValue() * 86400) % 86400);
        }
        return LocalTime.parse(cell == null ? "" : formatter.formatCellValue(cell).trim());
    }

    private String getCellText(Row row, int cellIndex, DataFormatter formatter) {
        if (cellIndex < 0) return "";
        Cell cell = row.getCell(cellIndex);
        return (cell == null) ? "" : formatter.formatCellValue(cell).trim();
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        if (!value.trim().matches("[0-9]+")) throw new IllegalArgumentException("Expected a nonnegative integer: " + value);
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Integer out of range: " + value); }
    }

    private String extractTeacherId(String raw) {
        if (raw.contains("-")) return raw.split("-")[0].trim().toUpperCase(Locale.ROOT);
        return "GV_" + raw.toUpperCase().replaceAll("\\s+", "");
    }

    private String extractTeacherName(String raw) {
        if (raw.contains("-")) return raw.split("-", 2)[1].trim();
        return raw.trim();
    }
}
