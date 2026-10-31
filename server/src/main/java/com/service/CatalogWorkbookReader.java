package com.service;

import com.dto.CatalogImportDTO.Kind;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.text.Normalizer;
import java.util.*;

/** Reads values only: formulas are rejected rather than evaluated or trusted. */
final class CatalogWorkbookReader {
    record SourceRow(int number, Map<String, String> values, List<String> errors) {}
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("ma gv", "lecturerCode"), Map.entry("ma giang vien", "lecturerCode"),
            Map.entry("ho ten", "fullName"), Map.entry("ho ten giang vien", "fullName"), Map.entry("ten giang vien", "fullName"),
            Map.entry("ma khoa", "departmentCode"), Map.entry("loai gv", "lecturerType"), Map.entry("loai giang vien", "lecturerType"),
            Map.entry("ma tai khoan", "accountTeacherId"), Map.entry("ma tai khoan gv", "accountTeacherId"),
            Map.entry("ma ho so lich cu", "existingScheduleTeacherId"),
            Map.entry("trang thai", "active"), Map.entry("active", "active"),
            Map.entry("ma phong", "roomCode"), Map.entry("ten phong", "roomName"),
            Map.entry("loai phong", "roomType"), Map.entry("suc chua", "capacity"), Map.entry("toa nha", "building"),
            Map.entry("ma mon", "courseCode"), Map.entry("ten mon", "courseName"), Map.entry("ten hoc phan", "courseName"),
            Map.entry("khoa", "cohort"), Map.entry("khoa sinh vien", "cohort"),
            Map.entry("trong so lt", "theoryWeight"), Map.entry("trong so th", "practiceWeight"));

    List<SourceRow> read(MultipartFile file, Kind kind) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024 || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Cần file .xlsx không rỗng, tối đa 10 MB.");
        try (var input = file.getInputStream(); var book = new XSSFWorkbook(input)) {
            if (book.getNumberOfSheets() == 0) throw new IllegalArgumentException("File không có sheet dữ liệu.");
            var sheet = book.getSheetAt(0);
            if (sheet.getLastRowNum() > 10000) throw new IllegalArgumentException("Sheet vượt 10000 dòng; bỏ các dòng định dạng thừa trước khi nhập.");
            var header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Dòng 1 phải là tiêu đề cột của mẫu danh mục.");
            var columns = new LinkedHashMap<String, Integer>();
            for (var cell : header) {
                String name = normalize(formatter.formatCellValue(cell)).replaceAll("\\s*\\(.*\\)\\s*$", "");
                String field = HEADERS.get(name);
                if (field != null && columns.putIfAbsent(field, cell.getColumnIndex()) != null)
                    throw new IllegalArgumentException("Tiêu đề cột bị lặp: " + formatter.formatCellValue(cell));
            }
            List<String> required = switch (kind) {
                case LECTURERS -> List.of("lecturerCode", "fullName");
                case ROOMS -> List.of("roomCode", "roomType", "capacity");
                case ASSIGNMENTS -> List.of("lecturerCode", "courseCode", "courseName", "cohort", "theoryWeight", "practiceWeight");
            };
            if (!columns.keySet().containsAll(required)) throw new IllegalArgumentException("Thiếu cột bắt buộc. Hãy tải mẫu danh mục tương ứng và giữ tiêu đề dòng 1.");
            var rows = new ArrayList<SourceRow>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null) continue;
                boolean empty = true;
                for (var cell : row) if (!formatter.formatCellValue(cell).isBlank()) { empty = false; break; }
                if (empty) continue;
                if (rows.size() >= 1000) throw new IllegalArgumentException("Mỗi lần nhập tối đa 1000 dòng dữ liệu.");
                var values = new LinkedHashMap<String, String>();
                var errors = new ArrayList<String>();
                columns.forEach((field, column) -> {
                    var cell = row.getCell(column);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) errors.add("Cột " + field + " chứa công thức; hãy dán giá trị trước khi nhập.");
                    values.put(field, formatter.formatCellValue(cell).strip());
                });
                rows.add(new SourceRow(index + 1, values, errors));
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("File chưa có dòng dữ liệu.");
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file .xlsx. Kiểm tra định dạng và nội dung file.", e);
        }
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
