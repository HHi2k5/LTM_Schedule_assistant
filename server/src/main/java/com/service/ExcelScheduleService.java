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
    private final RoomRepository roomRepository;
    private final ScheduleWriteLock writeLock;

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})");

    /**
     * 1. Giảng viên import lịch cá nhân từ file Excel 9 cột
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
            importedPeriods = readPeriods(workbook, formatter);

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

                if (dayStr.isBlank() || startPeriodStr.isBlank()) throw new IllegalArgumentException("Thiếu thứ hoặc tiết bắt đầu.");

                if (!courseCodeCell.isBlank()) lastCourseCode = courseCodeCell;
                if (!courseNameCell.isBlank()) lastCourseName = courseNameCell;
                if (!groupCell.isBlank()) lastGroup = groupCell;

                if (lastCourseName.isBlank()) throw new IllegalArgumentException("Thiếu tên môn.");

                int dayOfWeek = parseDayOfWeek(dayStr);
                int startPeriod = parseIntSafe(startPeriodStr, 1);
                int numPeriods = parseIntSafe(getCellText(row, 6, formatter), 2);
                String room = getCellText(row, 7, formatter);
                String studyTimeRange = getCellText(row, 8, formatter);

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
                        .build());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("MonHoc dòng " + (i + 1) + ": " + e.getMessage());
                }
            }
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
                .periods(getDefault12Periods())
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
     * 2. Xử lý trọn bộ 3 file Excel:
     * - File 1 (planFile): Kế hoạch mở lớp (có STT, Mã môn, Tên học phần, Sĩ số, Khóa, Số lớp mở, Tuần BĐ, Tiết BĐ, Số tiết, Thứ 2..Thứ 7)
     * - File 2 (teacherFile): Danh sách Giảng viên - Môn học - Khóa
     * - File 3 (roomFile): Danh sách Phòng học (Lưu vào bảng rooms trong MySQL)
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

        // BƯỚC 2: Đọc File 2 (Danh sách giảng viên theo Môn học & Khóa)
        Map<String, List<TeacherCandidateDTO>> teacherMap = parseTeacherExcel(teacherFile);

        // BƯỚC 3: Đọc File 1 (Kế hoạch mở lớp)
        List<CourseDemandDTO> courseDemands = parsePlanExcel(planFile, teacherMap);
        if (courseDemands.isEmpty()) throw new IllegalArgumentException("Kế hoạch không có dòng hợp lệ.");
        writeLock.acquire();
        List<Room> availableRooms = parseAndSaveRoomExcel(roomFile, resolvedSchoolId);

        SchoolConfigDTO targetSchool = SchoolConfigDTO.builder()
                .schoolId(resolvedSchoolId)
                .schoolName(schoolName != null ? schoolName : "Học viện Công nghệ Bưu chính Viễn thông")
                .colorCode("#dc2626")
                .periods(getDefault12Periods())
                .build();

        SchoolWideScheduleRequest request = SchoolWideScheduleRequest.builder()
                .semesterStartDate(resolvedStart)
                .totalWeeks(16)
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

    /**
     * Đọc File 1: Kế hoạch mở lớp
     * Tự động nhận diện vị trí cột từ dòng Header (hỗ trợ cả cột Tiết BĐ + Số tiết + Cột Thứ 2..Thứ 7 đánh dấu 'x')
     */
    private List<CourseDemandDTO> parsePlanExcel(
            MultipartFile planFile,
            Map<String, List<TeacherCandidateDTO>> teacherMap) throws Exception {

        List<CourseDemandDTO> demands = new ArrayList<>();

        try (InputStream is = planFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // Mặc định vị trí cột theo đúng ảnh chụp màn hình:
            // [0] STT | [1] Mã môn | [2] Tên học phần | [3] Sĩ số | [4] Khóa | [5] Số lớp mở | [6] Tuần BĐ | [7] Số tiết
            int colCredits = -1;
            int colStt = 0;
            int colCode = 1;
            int colName = 2;
            int colSize = 3;
            int colCohort = 4;
            int colNumClasses = 5;
            int colStartWeek = 6;
            int colStartPeriod = -1; // Nếu có cột "Tiết BĐ" riêng
            int colNumPeriods = 7;   // Cột "Số tiết"
            int colDirectDay = -1;   // Nếu có cột "Thứ" ghi trực tiếp số 2..7
            Map<Integer, Integer> dayColumnsMap = new HashMap<>(); // Map<ColumnIndex, DayOfWeek (2..8)> cho các cột "Thứ 2", "Thứ 3"...

            // Quét 5 dòng đầu để tự động map chính xác tên cột
            int headerRowIdx = -1;
            for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String header = getCellText(row, c, formatter).toLowerCase();
                    if (header.isBlank()) continue;

                    if (header.contains("tín chỉ") || header.equals("credits")) { colCredits = c; headerRowIdx = r; }
                    else if (header.equals("stt")) { colStt = c; headerRowIdx = r; }
                    else if (header.contains("mã môn") || header.contains("mã hp")) { colCode = c; headerRowIdx = r; }
                    else if (header.contains("tên học phần") || header.contains("tên môn")) { colName = c; headerRowIdx = r; }
                    else if (header.contains("sĩ số")) { colSize = c; }
                    else if (header.contains("khóa")) { colCohort = c; }
                    else if (header.contains("số lớp")) { colNumClasses = c; }
                    else if (header.contains("tuần")) { colStartWeek = c; }
                    else if (header.contains("tiết bđ") || header.contains("tiết bắt đầu")) { colStartPeriod = c; }
                    else if (header.contains("số tiết")) { colNumPeriods = c; }
                    else if (header.matches("thứ\\s*2") || header.equals("t2")) { dayColumnsMap.put(c, 2); }
                    else if (header.matches("thứ\\s*3") || header.equals("t3")) { dayColumnsMap.put(c, 3); }
                    else if (header.matches("thứ\\s*4") || header.equals("t4")) { dayColumnsMap.put(c, 4); }
                    else if (header.matches("thứ\\s*5") || header.equals("t5")) { dayColumnsMap.put(c, 5); }
                    else if (header.matches("thứ\\s*6") || header.equals("t6")) { dayColumnsMap.put(c, 6); }
                    else if (header.matches("thứ\\s*7") || header.equals("t7")) { dayColumnsMap.put(c, 7); }
                    else if (header.contains("chủ nhật") || header.equals("cn")) { dayColumnsMap.put(c, 8); }
                    else if (header.equals("thứ")) { colDirectDay = c; }
                }
            }

            // Nếu không tìm thấy cột Thứ 2..Thứ 7 bằng tiêu đề, mặc định các cột từ H/I trở đi (như trên ảnh: cột 8->13 là Thứ 2->Thứ 7)
            if (dayColumnsMap.isEmpty() && colDirectDay == -1) {
                int baseDayCol = (colStartPeriod != -1) ? Math.max(colStartPeriod, colNumPeriods) + 1 : 8;
                for (int d = 2; d <= 7; d++) {
                    dayColumnsMap.put(baseDayCol + (d - 2), d);
                }
            }

            // Bộ đếm để xoay vòng (round-robin) chia đều các dòng của cùng 1 (Mã môn + Khóa) cho danh sách giảng viên
            Map<String, Integer> teacherRoundRobinCounter = new HashMap<>();

            if (headerRowIdx < 0) throw new IllegalArgumentException("Missing plan column headers.");
            int startScanRow = headerRowIdx + 1;
            for (int i = startScanRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String sttStr = getCellText(row, colStt, formatter);
                String courseCode = getCellText(row, colCode, formatter).toUpperCase();
                String courseName = getCellText(row, colName, formatter);
                String classSizeStr = getCellText(row, colSize, formatter);

                if (rowIsEmpty(row, formatter)) continue;
                if (courseCode.isBlank() || courseName.isBlank())
                    throw new IllegalArgumentException("Plan row " + (i + 1) + ": missing course code/name.");

                String creditsText = colCredits < 0 ? "" : getCellText(row, colCredits, formatter);
                Integer credits = creditsText.isBlank() ? null : parseIntSafe(creditsText, -1);
                if (credits != null && (credits < 1 || credits > 30)) {
                    throw new IllegalArgumentException("Dòng " + (i + 1) + ": số tín chỉ phải từ 1 đến 30.");
                }
                int classSize = parseIntSafe(classSizeStr, 50);
                String cohort = getCellText(row, colCohort, formatter);
                int numClasses = parseIntSafe(getCellText(row, colNumClasses, formatter), 1);
                int startWeek = parseIntSafe(getCellText(row, colStartWeek, formatter), 1);
                int numPeriods = parseIntSafe(getCellText(row, colNumPeriods, formatter), 3);
                if (classSize < 1 || classSize > 1000 || numClasses < 1 || numClasses > 500 || startWeek < 1 || startWeek > 16
                        || numPeriods < 1 || numPeriods > 30) throw new IllegalArgumentException("Dòng " + (i + 1) + ": sĩ số/lớp/tuần/tiết không hợp lệ.");

                Integer fixedStartPeriod = null;
                if (colStartPeriod != -1) {
                    String spText = getCellText(row, colStartPeriod, formatter);
                    if (!spText.isBlank()) {
                        fixedStartPeriod = parseIntSafe(spText, 1);
                    }
                }

                // Dò xem dòng này học vào Thứ mấy (kiểm tra cột Thứ trực tiếp hoặc các cột Thứ 2..Thứ 7 có đánh dấu 'x' / ghi tiết)
                Integer fixedDay = null;
                if (colDirectDay != -1) {
                    String dText = getCellText(row, colDirectDay, formatter);
                    if (!dText.isBlank()) fixedDay = parseDayOfWeek(dText);
                }
                if (fixedDay == null) {
                    for (Map.Entry<Integer, Integer> entry : dayColumnsMap.entrySet()) {
                        String cellMark = getCellText(row, entry.getKey(), formatter);
                        if (!cellMark.isBlank()) {
                            fixedDay = entry.getValue();
                            // Nếu trong ô Thứ 2..Thứ 7 ghi số tiết bắt đầu (VD: "1-3" hoặc "4") thay vì chữ "x"
                            if (fixedStartPeriod == null && cellMark.matches(".*\\d+.*")) {
                                String firstNum = cellMark.split("[-,]")[0].trim();
                                fixedStartPeriod = parseIntSafe(firstNum, 1);
                            }
                            break;
                        }
                    }
                }

                // Tìm danh sách giảng viên phụ trách (Mã môn + Khóa) từ File 2
                String keyWithCohort = courseCode + "_" + cohort;
                List<TeacherCandidateDTO> candidates = teacherMap.get(keyWithCohort);
                if (candidates == null || candidates.isEmpty()) {
                    candidates = teacherMap.get(courseCode);
                }

                List<TeacherCandidateDTO> assignedForThisRow;
                if (candidates != null && !candidates.isEmpty()) {
                    // Chia đều từng dòng (lớp) cho các giảng viên trong danh sách theo vòng tròn (Round-Robin)
                    int rrIdx = teacherRoundRobinCounter.getOrDefault(keyWithCohort, 0);
                    TeacherCandidateDTO picked = candidates.get(rrIdx % candidates.size());
                    teacherRoundRobinCounter.put(keyWithCohort, rrIdx + 1);

                    assignedForThisRow = List.of(TeacherCandidateDTO.builder()
                            .teacherId(picked.getTeacherId())
                            .teacherName(picked.getTeacherName())
                            .assignedClasses(numClasses)
                            .build());
                } else {
                    // Nếu không truyền file GV hoặc môn đó chưa có trong file GV -> Tự tạo GV theo môn & khóa
                    int rrIdx = teacherRoundRobinCounter.getOrDefault(keyWithCohort, 0);
                    teacherRoundRobinCounter.put(keyWithCohort, rrIdx + 1);
                    int gvGroup = (rrIdx / 3) + 1; // Mỗi GV dạy tối đa 3 lớp để chia đều
                    assignedForThisRow = List.of(TeacherCandidateDTO.builder()
                            .teacherId(String.format("GV_%s_%02d", courseCode, gvGroup))
                            .teacherName(String.format("GV %s (Nhóm %d)", courseName, gvGroup))
                            .assignedClasses(numClasses)
                            .build());
                }

                demands.add(CourseDemandDTO.builder()
                        .credits(credits)
                        .stt(sttStr.isBlank() ? String.valueOf(demands.size() + 1) : sttStr)
                        .courseCode(courseCode)
                        .courseName(courseName)
                        .classSize(classSize)
                        .cohort(cohort)
                        .totalClassesToOpen(numClasses)
                        .startWeek(startWeek)
                        .fixedDayOfWeek(fixedDay)
                        .fixedStartPeriod(fixedStartPeriod)
                        .numPeriodsPerSession(numPeriods)
                        .hasPractice(true)
                        .teachers(assignedForThisRow)
                        .build());
            }
        }
        return demands;
    }

    /**
     * Đọc File 2: Danh sách Giảng viên - Môn học - Khóa
     * Hỗ trợ linh hoạt các cột: Mã môn | Tên môn | Khóa | Mã GV | Tên GV
     */
    private Map<String, List<TeacherCandidateDTO>> parseTeacherExcel(MultipartFile teacherFile) throws Exception {
        Map<String, List<TeacherCandidateDTO>> map = new HashMap<>();
        if (teacherFile == null || teacherFile.isEmpty()) return map;
        validateFile(teacherFile);

        try (InputStream is = teacherFile.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int colCourseCode = 0;
            int colCohort = 2;
            int colTeacherId = 3;
            int colTeacherName = 4;

            // Tự động nhận diện cột ở dòng tiêu đề
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellText(headerRow, c, formatter).toLowerCase();
                    if (h.contains("mã môn") || h.contains("mã hp")) colCourseCode = c;
                    else if (h.contains("khóa")) colCohort = c;
                    else if (h.contains("mã gv") || h.contains("mã giảng viên")) colTeacherId = c;
                    else if (h.contains("tên gv") || h.contains("họ tên") || h.contains("giảng viên")) colTeacherName = c;
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String courseCode = getCellText(row, colCourseCode, formatter).toUpperCase();
                String cohort = getCellText(row, colCohort, formatter);
                String teacherId = getCellText(row, colTeacherId, formatter).toUpperCase(Locale.ROOT);
                String teacherName = getCellText(row, colTeacherName, formatter);

                // Hỗ trợ trường hợp file chỉ có 3 cột: [0] Mã môn | [1] Giảng viên | [2] Khóa
                if (teacherId.isBlank() && teacherName.isBlank()) {
                    String col1 = getCellText(row, 1, formatter);
                    String col2 = getCellText(row, 2, formatter);
                    if (!col1.isBlank()) {
                        teacherId = extractTeacherId(col1);
                        teacherName = extractTeacherName(col1);
                        cohort = col2;
                    }
                } else if (teacherId.isBlank()) {
                    teacherId = extractTeacherId(teacherName);
                } else if (teacherName.isBlank()) {
                    teacherName = extractTeacherName(teacherId);
                }

                if (rowIsEmpty(row, formatter)) continue;
                if (courseCode.isBlank() || teacherId.isBlank()) throw new IllegalArgumentException("Danh sách GV dòng " + (i + 1) + ": thiếu mã môn/GV.");
                ScheduleValidation.text(teacherId, 50, "Dòng " + (i + 1) + ": mã GV");
                ScheduleValidation.text(teacherName, 100, "Dòng " + (i + 1) + ": tên GV");

                TeacherCandidateDTO candidate = TeacherCandidateDTO.builder()
                        .teacherId(teacherId)
                        .teacherName(teacherName)
                        .build();

                // Lưu theo cả key "MãMôn_Khóa" và key "MãMôn" để tra cứu chính xác
                if (!cohort.isBlank()) {
                    String keyCohort = courseCode + "_" + cohort;
                    map.putIfAbsent(keyCohort, new ArrayList<>());
                    addUniqueTeacher(map.get(keyCohort), candidate);
                }
                map.putIfAbsent(courseCode, new ArrayList<>());
                addUniqueTeacher(map.get(courseCode), candidate);
            }
        }
        return map;
    }

    private void addUniqueTeacher(List<TeacherCandidateDTO> list, TeacherCandidateDTO candidate) {
        if (list.stream().noneMatch(t -> t.getTeacherId().equalsIgnoreCase(candidate.getTeacherId()))) {
            list.add(candidate);
        }
    }

    /**
     * Đọc File 3: Danh sách Phòng học và lưu vào bảng `rooms` trong MySQL
     * Cột hỗ trợ: [0] Mã phòng | [1] Loại phòng (Lý thuyết / Thực hành / LAB) | [2] Sức chứa (Capacity) | [3] Tòa nhà
     */
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

        // Nếu không truyền file phòng học, lấy từ Database MySQL
        List<Room> dbRooms = roomRepository.findBySchoolCodeOrderByCapacityAsc(schoolCode);
        if (!dbRooms.isEmpty()) {
            return dbRooms;
        }

        // An empty catalogue requires explicit room input instead of invented inventory.
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

    private List<PeriodTimeDTO> readPeriods(Workbook workbook, DataFormatter formatter) {
        Sheet sheet = workbook.getSheet("TietHoc");
        if (sheet == null) throw new IllegalArgumentException("Thiếu sheet TietHoc (period, start, end); cần cung cấp bảng giờ thực của trường.");
        var periods = new ArrayList<PeriodTimeDTO>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || rowIsEmpty(row, formatter)) continue;
            try {
                periods.add(new PeriodTimeDTO(parseIntSafe(getCellText(row, 0, formatter), 0),
                        periodTime(row.getCell(1), formatter), periodTime(row.getCell(2), formatter)));
            } catch (RuntimeException e) { throw new IllegalArgumentException("TietHoc dòng " + (i + 1) + ": tiết/giờ không hợp lệ."); }
        }
        ScheduleValidation.periods(periods);
        return periods;
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
