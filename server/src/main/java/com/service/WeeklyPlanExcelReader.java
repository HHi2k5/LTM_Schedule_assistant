package com.service;

import com.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;

final class WeeklyPlanExcelReader {
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private record Table(Sheet sheet, int firstDataRow, Map<String, Integer> columns) {}

    /** Đọc kế hoạch mở lớp. Giảng viên không còn được gán ở bước này; trưởng bộ môn phân công sau khi lớp đã có phòng. */
    List<CourseDemandDTO> read(MultipartFile plan) throws Exception {
        var courses = new ArrayList<CourseDemandDTO>();
        validateFile(plan, "Kế hoạch mở lớp");

        try (var input = plan.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            var table = findPlanTable(workbook);
            boolean hasClassTypeCol = table.columns().containsKey("loai lop");
            boolean hasSoTietCol = table.columns().containsKey("so tiet");

            for (int i = table.firstDataRow(); i <= table.sheet().getLastRowNum(); i++) {
                Row row = table.sheet().getRow(i);
                if (empty(row)) continue;
                String label = "Kế hoạch dòng " + (i + 1) + ": ";
                String code = required(table, row, "ma mon", label).toUpperCase(Locale.ROOT);
                String cohort = required(table, row, "khoa", label);

                // Xác định loại lớp: Lý thuyết (LT) hay Thực hành (TH) từ cột "Loại lớp"
                String rawType = hasClassTypeCol ? value(table, row, "loai lop").toUpperCase(Locale.ROOT) : "";
                boolean isTheoryOnly = rawType.equals("LT") || rawType.contains("LY THUYET") || rawType.contains("LÝ THUYẾT") || rawType.equals("THEORY");
                boolean isPracticeOnly = rawType.equals("TH") || rawType.contains("THUC HANH") || rawType.contains("THỰC HÀNH") || rawType.contains("LAB") || rawType.equals("PRACTICE");

                int theoryPeriods = 0;
                int practicePeriods = 0;

                if (isTheoryOnly) {
                    theoryPeriods = hasSoTietCol && !value(table, row, "so tiet").isBlank()
                            ? integer(table, row, "so tiet", 1, 1000, label)
                            : integerOrDefault(table, row, "tong tiet lt", 30, label);
                    practicePeriods = 0;
                } else if (isPracticeOnly) {
                    theoryPeriods = 0;
                    practicePeriods = hasSoTietCol && !value(table, row, "so tiet").isBlank()
                            ? integer(table, row, "so tiet", 1, 1000, label)
                            : integerOrDefault(table, row, "tong tiet th", 30, label);
                } else {
                    theoryPeriods = integerOrDefault(table, row, "tong tiet lt", 30, label);
                    practicePeriods = integerOrDefault(table, row, "tong tiet th", 0, label);
                }

                if (theoryPeriods + practicePeriods == 0) {
                    throw new IllegalArgumentException(label + "số tiết LT/TH phải lớn hơn 0.");
                }

                int classSize = integer(table, row, "si so", 1, 1000, label);

                // Nếu dòng này được đánh dấu rõ là lớp Thực hành (TH), giữ nguyên sĩ số của dòng đó cho phòng LAB
                var course = CourseDemandDTO.builder()
                        .stt(String.valueOf(i + 1))
                        .courseCode(code)
                        .courseName(required(table, row, "ten hoc phan", label))
                        .cohort(cohort)
                        .classSize(classSize)
                        .totalClassesToOpen(integer(table, row, "so lop mo", 1, 500, label))
                        .credits(integerOrDefault(table, row, "so tin chi", 3, label))
                        .theoryPeriods(theoryPeriods)
                        .practicePeriods(practicePeriods)
                        .practiceGroupSize(isPracticeOnly ? classSize : null)
                        .studyStartDate(date(table, row, "tuan bat dau", label))
                        .studyEndDate(date(table, row, "tuan ket thuc", label))
                        .teachers(List.of())
                        .build();

                courses.add(course);
                if (courses.size() > 5000) throw new IllegalArgumentException("Kế hoạch tối đa 5000 dòng.");
            }
        }
        if (courses.isEmpty()) throw new IllegalArgumentException("Kế hoạch không có dòng hợp lệ.");
        return courses;
    }

    private Table findPlanTable(XSSFWorkbook workbook) {
        if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("Kế hoạch: thiếu sheet.");
        var sheet = workbook.getSheetAt(0);
        var baseRequired = List.of("ma mon", "ten hoc phan", "si so", "khoa", "so lop mo", "tuan bat dau", "tuan ket thuc");
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            var row = sheet.getRow(i);
            if (row == null) continue;
            var columns = new HashMap<String, Integer>();
            for (var cell : row) {
                String h = normalize(formatter.formatCellValue(cell));
                if (h.startsWith("tuan bat dau") || h.equals("tu ngay")) h = "tuan bat dau";
                if (h.startsWith("tuan ket thuc") || h.equals("den ngay")) h = "tuan ket thuc";
                if (h.contains("loai lop") || h.equals("loai") || h.contains("hinh thuc")) h = "loai lop";
                if (h.equals("tong tiet") || h.equals("so tiet")) h = "so tiet";
                if (!h.isEmpty()) columns.putIfAbsent(h, cell.getColumnIndex());
            }
            if (columns.keySet().containsAll(baseRequired)) {
                return new Table(sheet, i + 1, columns);
            }
        }
        throw new IllegalArgumentException("File Kế hoạch mở lớp thiếu các cột bắt buộc: Mã môn, Tên học phần, Loại lớp (LT/TH), Sĩ số, Khóa, Số lớp mở, Tuần bắt đầu, Tuần kết thúc.");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
    private String value(Table table, Row row, String key) {
        Integer column = table.columns().get(key);
        return column == null ? "" : formatter.formatCellValue(row.getCell(column)).trim();
    }
    private String required(Table table, Row row, String key, String label) {
        String text = value(table, row, key);
        if (text.isBlank()) throw new IllegalArgumentException(label + "thiếu cột/giá trị " + key);
        return text;
    }
    private int integer(Table table, Row row, String key, int min, int max, String label) {
        String text = required(table, row, key, label);
        try {
            if (!text.matches("[0-9]+")) throw new NumberFormatException();
            int number = Integer.parseInt(text);
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(label + key + " phải là số nguyên " + min + "–" + max); }
    }
    private int integerOrDefault(Table table, Row row, String key, int defaultVal, String label) {
        String text = value(table, row, key);
        if (text.isBlank()) return defaultVal;
        return integer(table, row, key, 0, 1000, label);
    }
    private LocalDate date(Table table, Row row, String key, String label) {
        var cell = row.getCell(table.columns().get(key));
        try {
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
                return cell.getLocalDateTimeCellValue().toLocalDate();
            String text = required(table, row, key, label);
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(text);
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeException | IllegalStateException e) { throw new IllegalArgumentException(label + key + " cần ngày hợp lệ dd/MM/yyyy hoặc yyyy-MM-dd."); }
    }
    private boolean empty(Row row) {
        if (row == null) return true;
        for (var cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }
    private void validateFile(MultipartFile file, String label) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024 || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException(label + ": cần file .xlsx không rỗng, tối đa 10 MB.");
    }
}
