package com.controller;

import com.dto.AutoScheduleResponse;
import com.dto.SchoolUploadRequest;
import com.dto.SchoolWideScheduleRequest;
import com.dto.WeekScheduleResponse;
import com.service.AutoScheduleService;
import com.service.ExcelScheduleService;
import com.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final AutoScheduleService autoScheduleService;
    private final ExcelScheduleService excelScheduleService;
    private final com.service.AuthService authService;

    // 1. Giảng viên up file Excel lịch trường (nếu up lại cùng schoolId sẽ tự đè lịch mới)
    @PostMapping(value = "/upload-excel-teacher", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTeacherScheduleExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("teacherId") String teacherId,
            @RequestParam(value = "teacherName", required = false) String teacherName,
            @RequestParam("schoolId") String schoolId,
            @RequestParam("schoolName") String schoolName,
            @RequestParam(value = "colorCode", defaultValue = "#2563eb") String colorCode,
            @RequestParam(value = "semesterStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semesterStartDate) {
        try {
            excelScheduleService.importTeacherScheduleFromExcel(
                    file, teacherId, teacherName, schoolId, schoolName, colorCode, semesterStartDate);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã đọc file Excel và cập nhật lịch trường " + schoolName + " thành công!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 2. Xóa lịch cũ của 1 trường cụ thể để giảng viên đổi lịch
    @DeleteMapping("/school")
    public ResponseEntity<?> deleteSchoolSchedule(
            @RequestParam("teacherId") String teacherId,
            @RequestParam("schoolId") String schoolId) {
        scheduleService.deleteSchoolSchedule(teacherId, schoolId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa toàn bộ lịch cũ của trường " + schoolId + "!"
        ));
    }

    // 3. Nhà trường up file Excel (Cột 1: Môn học, Cột 2: Giảng viên) -> Server tự xếp vào 35 phòng cố định
    @PostMapping(value = "/auto-generate-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> autoGenerateFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "schoolId", defaultValue = "PTIT") String schoolId,
            @RequestParam(value = "schoolName", defaultValue = "Học viện Công nghệ Bưu chính Viễn thông") String schoolName,
            @RequestParam(value = "semesterStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semesterStartDate) {
        try {
            AutoScheduleResponse response = excelScheduleService.autoScheduleFromExcel(
                    file, schoolId, schoolName, semesterStartDate);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 4. Client lấy kết quả thời khóa biểu theo tuần
    @GetMapping("/week/{weekIndex}")
    public ResponseEntity<WeekScheduleResponse> getWeekSchedule(
            @PathVariable int weekIndex,
            @RequestParam(defaultValue = "GV01") String teacherId) {
        return ResponseEntity.ok(scheduleService.getWeeklySchedule(teacherId, weekIndex));
    }
        @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody com.dto.AuthRequest request) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody com.dto.AuthRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}