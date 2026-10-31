package com.controller;

import com.dto.AutoScheduleResponse;
import com.dto.WeekScheduleResponse;
import com.model.Room;
import com.model.TeacherSubject;
import com.repository.RoomRepository;
import com.repository.TeacherSubjectRepository;
import com.security.AccountPrincipal;
import com.security.ScheduleAccess;
import com.service.ExcelScheduleService;
import com.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ExcelScheduleService excelScheduleService;
    private final com.service.AuthService authService;
    private final RoomRepository roomRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
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

    // 3. Xếp lịch tự động toàn trường (Chỉ cần 1 file Kế hoạch mở lớp nếu hệ thống đã lưu sẵn GV và Phòng học)
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

    // ==================== QUẢN LÝ PHÒNG HỌC LƯU SẴN (CRUD) ====================
    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(roomRepository.findBySchoolCodeOrderByCapacityAsc(schoolId));
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> saveOrUpdateRoom(@RequestBody Room payload) {
        String schoolCode = (payload.getSchoolCode() == null || payload.getSchoolCode().isBlank()) ? "PTIT" : payload.getSchoolCode().trim();
        String roomCode = payload.getRoomCode().trim();
        String rawType = payload.getRoomType() != null ? payload.getRoomType().toUpperCase(Locale.ROOT) : "THEORY";
        boolean isLab = payload.isLab() || rawType.contains("PRACTICE") || rawType.contains("LAB") || roomCode.toUpperCase(Locale.ROOT).contains("LAB");
        int capacity = payload.getCapacity() > 0 ? payload.getCapacity() : (isLab ? 30 : 60);

        String normalizedType = isLab ? "LAB_PRACTICE"
                : (capacity >= 120 ? "THEORY_LARGE" : (capacity >= 80 ? "THEORY_MEDIUM" : "THEORY_STANDARD"));

        Room room = (payload.getId() != null)
                ? roomRepository.findById(payload.getId()).orElse(new Room())
                : roomRepository.findBySchoolCodeAndRoomCodeIgnoreCase(schoolCode, roomCode).orElse(new Room());

        room.setRoomCode(roomCode);
        room.setRoomName((isLab ? "Phòng LAB " : "Phòng LT ") + roomCode);
        room.setRoomType(normalizedType);
        room.setCapacity(capacity);
        room.setLab(isLab);
        room.setBuilding((payload.getBuilding() == null || payload.getBuilding().isBlank()) ? "A2" : payload.getBuilding().trim());
        room.setSchoolCode(schoolCode);

        return ResponseEntity.ok(roomRepository.save(room));
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        roomRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa phòng học."));
    }

    // ==================== QUẢN LÝ GIẢNG VIÊN - MÔN HỌC LƯU SẴN (CRUD) ====================
    @GetMapping("/teacher-subjects")
    public ResponseEntity<List<TeacherSubject>> getTeacherSubjects(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(teacherSubjectRepository.findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc(schoolId));
    }

    @PostMapping("/teacher-subjects")
    public ResponseEntity<TeacherSubject> saveOrUpdateTeacherSubject(@RequestBody TeacherSubject payload) {
        String schoolCode = (payload.getSchoolCode() == null || payload.getSchoolCode().isBlank()) ? "PTIT" : payload.getSchoolCode().trim();
        String teacherId = payload.getTeacherId().trim().toUpperCase(Locale.ROOT);
        String courseCode = payload.getCourseCode().trim().toUpperCase(Locale.ROOT);
        String cohort = (payload.getCohort() == null || payload.getCohort().isBlank()) ? "*" : payload.getCohort().trim();

        TeacherSubject ts = (payload.getId() != null)
                ? teacherSubjectRepository.findById(payload.getId()).orElse(new TeacherSubject())
                : teacherSubjectRepository.findBySchoolCodeAndTeacherIdIgnoreCaseAndCourseCodeIgnoreCaseAndCohortIgnoreCase(
                        schoolCode, teacherId, courseCode, cohort).orElse(new TeacherSubject());

        ts.setSchoolCode(schoolCode);
        ts.setTeacherId(teacherId);
        ts.setTeacherName(payload.getTeacherName().trim());
        ts.setCourseCode(courseCode);
        ts.setCourseName(payload.getCourseName() != null ? payload.getCourseName().trim() : "");
        ts.setCohort(cohort);
        ts.setTheoryWeight(payload.getTheoryWeight() != null ? payload.getTheoryWeight() : 1);
        ts.setPracticeWeight(payload.getPracticeWeight() != null ? payload.getPracticeWeight() : 1);

        return ResponseEntity.ok(teacherSubjectRepository.save(ts));
    }

    @DeleteMapping("/teacher-subjects/{id}")
    public ResponseEntity<?> deleteTeacherSubject(@PathVariable Long id) {
        teacherSubjectRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa phân công giảng viên."));
    }

    // 5. Client lấy kết quả thời khóa biểu theo tuần
    @GetMapping("/week/{weekIndex}")
    public ResponseEntity<WeekScheduleResponse> getWeekSchedule(
            @PathVariable int weekIndex,
            @RequestParam(defaultValue = "GV01") String teacherId) {
        scheduleAccess.requireTeacherAccess(teacherId);
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