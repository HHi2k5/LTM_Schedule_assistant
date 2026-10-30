package com.service;

import com.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Strict, header-based contract for the new three-file workflow. */
final class WeeklyPlanExcelReader {
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private record Table(Sheet sheet, int firstDataRow, Map<String, Integer> columns) {}

    List<CourseDemandDTO> read(MultipartFile plan, MultipartFile teacherFile) throws Exception {
        var teachers = teachers(teacherFile);
        var courses = new ArrayList<CourseDemandDTO>();
        validateFile(plan, "Kế hoạch");
        try (var input = plan.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            var table = table(workbook, List.of("ma mon", "ten hoc phan", "si so", "khoa", "so lop mo", "tuan bat dau",
                    "tuan ket thuc", "so tin chi", "tong tiet lt", "tong tiet th"), "Kế hoạch");
            for (int i = table.firstDataRow(); i <= table.sheet().getLastRowNum(); i++) {
                Row row = table.sheet().getRow(i);
                if (empty(row)) continue;
                String label = "Kế hoạch dòng " + (i + 1) + ": ";
                String code = required(table, row, "ma mon", label).toUpperCase(Locale.ROOT);
                String cohort = required(table, row, "khoa", label);
                var candidates = teachers.get(code + ":" + cohort);
                if (candidates == null || candidates.isEmpty()) throw new IllegalArgumentException(label + "không có GV cho môn/khóa " + code + "/" + cohort);
                var course = CourseDemandDTO.builder().stt(String.valueOf(i + 1)).courseCode(code)
                        .courseName(required(table, row, "ten hoc phan", label)).cohort(cohort)
                        .classSize(integer(table, row, "si so", 1, 1000, label))
                        .totalClassesToOpen(integer(table, row, "so lop mo", 1, 500, label))
                        .credits(integer(table, row, "so tin chi", 1, 30, label))
                        .theoryPeriods(integer(table, row, "tong tiet lt", 0, 1000, label))
                        .practicePeriods(integer(table, row, "tong tiet th", 0, 1000, label))
                        .studyStartDate(date(table, row, "tuan bat dau", label))
                        .studyEndDate(date(table, row, "tuan ket thuc", label))
                        .teachers(candidates).build();
                courses.add(course);
                if (courses.size() > 5000) throw new IllegalArgumentException("Kế hoạch tối đa 5000 dòng.");
            }
        }
        if (courses.isEmpty()) throw new IllegalArgumentException("Kế hoạch không có dòng hợp lệ.");
        return courses;
    }

    private Map<String, List<TeacherCandidateDTO>> teachers(MultipartFile file) throws Exception {
        validateFile(file, "Danh sách GV (bắt buộc, có trọng số LT/TH)");
        var result = new LinkedHashMap<String, List<TeacherCandidateDTO>>();
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            var table = table(workbook, List.of("ma mon", "khoa", "ma gv", "ho ten giang vien", "trong so lt", "trong so th"), "Danh sách GV");
            int count = 0;
            for (int i = table.firstDataRow(); i <= table.sheet().getLastRowNum(); i++) {
                Row row = table.sheet().getRow(i);
                if (empty(row)) continue;
                if (++count > 10000) throw new IllegalArgumentException("Danh sách GV tối đa 10000 dòng.");
                String label = "Danh sách GV dòng " + (i + 1) + ": ";
                String key = required(table, row, "ma mon", label).toUpperCase(Locale.ROOT) + ":" + required(table, row, "khoa", label);
                var teacher = TeacherCandidateDTO.builder().teacherId(required(table, row, "ma gv", label).toUpperCase(Locale.ROOT))
                        .teacherName(required(table, row, "ho ten giang vien", label))
                        .theoryWeight(integer(table, row, "trong so lt", 0, 10000, label))
                        .practiceWeight(integer(table, row, "trong so th", 0, 10000, label)).build();
                var list = result.computeIfAbsent(key, ignored -> new ArrayList<>());
                var old = list.stream().filter(t -> t.getTeacherId().equals(teacher.getTeacherId())).findFirst();
                if (old.isPresent() && !old.get().equals(teacher)) throw new IllegalArgumentException(label + "trọng số/tên mâu thuẫn cho cùng GV–môn–khóa.");
                if (old.isEmpty()) list.add(teacher);
            }
        }
        return result;
    }

    private Table table(XSSFWorkbook workbook, List<String> required, String label) {
        if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException(label + ": thiếu sheet.");
        var sheet = workbook.getSheetAt(0);
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            var row = sheet.getRow(i);
            if (row == null) continue;
            var columns = new HashMap<String, Integer>();
            for (var cell : row) {
                String h = normalize(formatter.formatCellValue(cell));
                if (h.startsWith("tuan bat dau")) h = "tuan bat dau";
                if (h.startsWith("tuan ket thuc")) h = "tuan ket thuc";
                if (h.equals("ten gv") || h.equals("ho ten")) h = "ho ten giang vien";
                if (!h.isEmpty() && columns.putIfAbsent(h, cell.getColumnIndex()) != null)
                    throw new IllegalArgumentException(label + ": cột trùng " + h);
            }
            if (columns.keySet().containsAll(required)) return new Table(sheet, i + 1, columns);
        }
        throw new IllegalArgumentException(label + ": tải mẫu mới; cần các cột " + String.join(", ", required)
                + ". Mẫu xếp sẵn thứ/tiết cũ không dùng cho luồng ba file mới.");
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
