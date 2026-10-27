package com.controller;

import com.dto.AutoScheduleResponse;
import com.dto.WeekScheduleResponse;
import com.model.Room;
import com.repository.RoomRepository;
import com.service.ExcelScheduleService;
import com.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.security.AccountPrincipal;
import com.security.ScheduleAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ExcelScheduleService excelScheduleService;
    private final com.service.AuthService authService;
    private final RoomRepository roomRepository;
    private final ScheduleAccess scheduleAccess;

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
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semesterStartDate) throws Exception {
        scheduleAccess.requireTeacherAccess(teacherId);
        excelScheduleService.importTeacherScheduleFromExcel(
                file, teacherId, teacherName, schoolId, schoolName, colorCode, semesterStartDate);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã đọc file Excel và cập nhật lịch trường " + schoolName + " thành công!"
        ));
    }

    // 2. Xóa lịch cũ của 1 trường cụ thể để giảng viên đổi lịch
    @DeleteMapping("/school")
    public ResponseEntity<?> deleteSchoolSchedule(
            @RequestParam("teacherId") String teacherId,
            @RequestParam("schoolId") String schoolId) {
        scheduleAccess.requireTeacherAccess(teacherId);
        scheduleService.deleteSchoolSchedule(teacherId, schoolId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa toàn bộ lịch cũ của trường " + schoolId + "!"
        ));
    }

    // 3. Nhà trường up bộ 3 file Excel (Kế hoạch mở lớp + Danh sách Giảng viên + Danh sách Phòng học)
    // Hỗ trợ gọi qua cả 2 đường dẫn: /auto-generate-3files và /auto-generate-excel
    @PostMapping(value = {"/auto-generate-excel", "/auto-generate-3files"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> autoGenerateFromExcel(
            @RequestParam(value = "planFile", required = false) MultipartFile planFile,
            @RequestParam(value = "file", required = false) MultipartFile legacyFile,
            @RequestParam(value = "teacherFile", required = false) MultipartFile teacherFile,
            @RequestParam(value = "roomFile", required = false) MultipartFile roomFile,
            @RequestParam(value = "schoolId", defaultValue = "PTIT") String schoolId,
            @RequestParam(value = "schoolName", defaultValue = "Học viện Công nghệ Bưu chính Viễn thông") String schoolName,
            @RequestParam(value = "semesterStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semesterStartDate) throws Exception {
        MultipartFile mainPlanFile = (planFile != null && !planFile.isEmpty()) ? planFile : legacyFile;
        if (mainPlanFile == null || mainPlanFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Vui lòng chọn file Excel Kế hoạch mở lớp!"
            ));
        }

        AutoScheduleResponse response = planFile != null && !planFile.isEmpty()
                ? excelScheduleService.autoScheduleFrom3Files(
                        mainPlanFile, teacherFile, roomFile, schoolId, schoolName, semesterStartDate)
                : excelScheduleService.autoScheduleFromExcel(
                        legacyFile, schoolId, schoolName, semesterStartDate);
        return ResponseEntity.ok(response);
    }

    // 4. Lấy danh sách toàn bộ phòng học đang lưu trong MySQL (sắp xếp theo sức chứa tăng dần)
    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(roomRepository.findBySchoolCodeOrderByCapacityAsc(schoolId));
    }

    // 5. Client lấy kết quả thời khóa biểu theo tuần
    @GetMapping("/week/{weekIndex}")
    public ResponseEntity<WeekScheduleResponse> getWeekSchedule(
            @PathVariable int weekIndex,
            @RequestParam(defaultValue = "GV01") String teacherId) {
        scheduleAccess.requireTeacherAccess(teacherId);
        return ResponseEntity.ok(scheduleService.getWeeklySchedule(teacherId, weekIndex));
    }

    // 6. Đăng ký tài khoản
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody com.dto.AuthRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // 7. Đăng nhập hệ thống
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody com.dto.AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> current(@AuthenticationPrincipal AccountPrincipal account) {
        return ResponseEntity.ok(authService.current(account));
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal AccountPrincipal account,
            @RequestBody com.dto.ChangePasswordRequest request) {
        authService.changePassword(account, request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đổi mật khẩu thành công."));
    }
}
