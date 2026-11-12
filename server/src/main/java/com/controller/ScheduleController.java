package com.controller;

import com.dto.AutoScheduleResponse;
import com.dto.TeacherAccountDTO;
import com.dto.WeekScheduleResponse;
import com.model.Room;
import com.security.AccountPrincipal;
import com.security.ScheduleAccess;
import com.service.CatalogService;
import com.service.ExcelScheduleService;
import com.service.ScheduleService;
import com.service.TeacherDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ExcelScheduleService excelScheduleService;
    private final com.service.AuthService authService;
    private final CatalogService catalogService;
    private final TeacherDirectoryService teacherDirectory;
    private final ScheduleAccess scheduleAccess;

    // 1. Giảng viên up file Excel lịch trường
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

    // 2. Xóa lịch cũ của 1 trường cụ thể
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

    // 2a. Giảng viên xem danh sách môn để chọn xóa (môn khoa phân được đánh dấu, không xóa được)
    @GetMapping("/personal-courses")
    public ResponseEntity<List<com.dto.PersonalCourseDTO>> listPersonalCourses(@RequestParam("teacherId") String teacherId) {
        return ResponseEntity.ok(scheduleService.listTeacherCourses(teacherId));
    }

    // 2a'. Giảng viên xóa các môn cá nhân đã chọn (lịch khoa phân luôn được giữ)
    @PostMapping("/personal-courses/delete")
    public ResponseEntity<?> deletePersonalCourses(@RequestBody com.dto.PersonalCourseDeleteRequest request) {
        int removed = scheduleService.deletePersonalCourses(request.teacherId(), request.courseIds());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "removed", removed,
                "message", "Đã xóa " + removed + " môn cá nhân. Lịch khoa phân được giữ nguyên."
        ));
    }

    // 2b. Admin xóa toàn bộ lịch đã xếp của Khoa (chỉ khi Admin bấm xóa mới mất)
    @DeleteMapping("/department-schedule")
    public ResponseEntity<?> deleteDepartmentSchedule(@RequestParam(defaultValue = "PTIT") String schoolId) {
        int removed = scheduleService.deleteEntireDepartmentSchedule(schoolId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa toàn bộ lịch của khoa " + schoolId + " (" + removed + " bản ghi giảng viên)!"
        ));
    }

    // 3b. Trưởng bộ môn xếp lịch bổ sung cho giảng viên thỉnh giảng của bộ môn mình
    @PostMapping("/visiting-schedule")
    public ResponseEntity<AutoScheduleResponse> scheduleVisitingTeacher(
            @RequestBody com.dto.VisitingTeacherRequest request) {
        return ResponseEntity.ok(scheduleService.scheduleVisitingTeacher(request));
    }

    // 3. Admin xếp các lớp của khoa vào phòng (chưa gán giảng viên; trưởng bộ môn phân công sau)
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

    // ==================== QUẢN LÝ PHÒNG HỌC ====================
    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(catalogService.listRooms(schoolId));
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> saveOrUpdateRoom(@RequestBody Room payload) {
        return ResponseEntity.ok(catalogService.saveRoom(payload));
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        catalogService.deleteRoom(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa phòng học."));
    }

    // ==================== QUẢN LÝ GIẢNG VIÊN (BỘ MÔN, LOẠI GV, MÔN PHỤ TRÁCH / DẠY THAY) ====================
    @GetMapping("/teacher-subjects")
    public ResponseEntity<List<TeacherAccountDTO>> getTeacherSubjects(
            @RequestParam(defaultValue = "PTIT") String schoolId,
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(teacherDirectory.list(schoolId, departmentId));
    }

    @PostMapping("/teacher-subjects")
    public ResponseEntity<?> saveOrUpdateTeacherSubject(@RequestBody TeacherAccountDTO payload) {
        return ResponseEntity.ok(teacherDirectory.save(payload));
    }

    @DeleteMapping("/teacher-subjects/{id}")
    public ResponseEntity<?> deleteTeacherSubject(@PathVariable Long id) {
        teacherDirectory.deleteAssignment(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa môn khỏi giảng viên."));
    }

    // 5. Client lấy kết quả thời khóa biểu theo tuần
    @GetMapping("/week/{weekIndex}")
    public ResponseEntity<WeekScheduleResponse> getWeekSchedule(
            @PathVariable int weekIndex,
            @RequestParam(defaultValue = "GV01") String teacherId) {
        return ResponseEntity.ok(scheduleService.getWeeklySchedule(teacherId, weekIndex));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody com.dto.AuthRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

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