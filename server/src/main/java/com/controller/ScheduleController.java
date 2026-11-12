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

import java.text.Normalizer;
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

    // Hàm tiện ích tạo mật khẩu mặc định: Tên giảng viên không dấu viết liền (thường) + Mã GV
    // VD: "Đào Ngọc Phong" + "GV01" -> "daongocphongGV01"
    private String buildDefaultPassword(String fullName, String teacherId) {
        if (fullName == null) fullName = "";
        String noAccent = Normalizer.normalize(fullName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return noAccent + teacherId.trim().toUpperCase(Locale.ROOT);
    }

    // ==================== QUẢN LÝ GIẢNG VIÊN (1 GV DẠY NHIỀU MÔN + TỰ SINH TK & MK) ====================
    @GetMapping("/teacher-subjects")
    public ResponseEntity<List<TeacherAccountDTO>> getTeacherSubjects(@RequestParam(defaultValue = "PTIT") String schoolId) {
        List<TeacherSubject> assignments = teacherSubjectRepository.findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc(schoolId);
        Map<String, Teacher> teacherMap = new HashMap<>();
        teacherRepository.findAll().forEach(t -> teacherMap.put(t.getTeacherId().toUpperCase(Locale.ROOT), t));

        Map<String, TeacherAccountDTO> grouped = new LinkedHashMap<>();

        for (TeacherSubject ts : assignments) {
            String tId = ts.getTeacherId().trim().toUpperCase(Locale.ROOT);
            Teacher acc = teacherMap.get(tId);
            String username = (acc != null && acc.getUsername() != null && !acc.getUsername().isBlank())
                    ? acc.getUsername()
                    : tId;
            String defaultPwd = buildDefaultPassword(ts.getTeacherName(), tId);

            TeacherAccountDTO dto = grouped.computeIfAbsent(tId, k -> TeacherAccountDTO.builder()
                    .schoolCode(ts.getSchoolCode())
                    .teacherId(tId)
                    .teacherName(ts.getTeacherName())
                    .username(username)
                    .defaultPassword(defaultPwd)
                    .subjects(new ArrayList<>())
                    .build());

            dto.getSubjects().add(TeacherAccountDTO.SubjectAssignment.builder()
                    .id(ts.getId())
                    .courseCode(ts.getCourseCode())
                    .courseName(ts.getCourseName())
                    .cohort(ts.getCohort())
                    .theoryWeight(ts.getTheoryWeight())
                    .practiceWeight(ts.getPracticeWeight())
                    .build());
        }

        return ResponseEntity.ok(new ArrayList<>(grouped.values()));
    }

    @Transactional
    @PostMapping("/teacher-subjects")
    public ResponseEntity<?> saveOrUpdateTeacherSubject(@RequestBody TeacherAccountDTO payload) {
        String schoolCode = (payload.getSchoolCode() == null || payload.getSchoolCode().isBlank()) ? "PTIT" : payload.getSchoolCode().trim();
        if (payload.getTeacherId() == null || payload.getTeacherId().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Mã giảng viên.");
        }
        if (payload.getTeacherName() == null || payload.getTeacherName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Họ tên giảng viên.");
        }

        // Gom danh sách môn học (hỗ trợ cả mảng subjects nhiều môn lẫn trường courseCode đơn lẻ)
        List<TeacherAccountDTO.SubjectAssignment> subjectList = new ArrayList<>();
        if (payload.getSubjects() != null && !payload.getSubjects().isEmpty()) {
            subjectList.addAll(payload.getSubjects());
        } else if (payload.getCourseCode() != null && !payload.getCourseCode().isBlank()) {
            subjectList.add(TeacherAccountDTO.SubjectAssignment.builder()
                    .id(payload.getSubjectAssignmentId())
                    .courseCode(payload.getCourseCode())
                    .courseName(payload.getCourseName())
                    .cohort(payload.getCohort())
                    .theoryWeight(payload.getTheoryWeight())
                    .practiceWeight(payload.getPracticeWeight())
                    .build());
        }

        if (subjectList.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng thêm ít nhất một môn học cho giảng viên.");
        }

        String teacherId = payload.getTeacherId().trim().toUpperCase(Locale.ROOT);
        String teacherName = payload.getTeacherName().trim();

        // Tự động tạo Tài khoản = Mã GV, Mật khẩu mặc định = tên không dấu viết liền + Mã GV
        String generatedUsername = teacherId;
        String generatedPassword = buildDefaultPassword(teacherName, teacherId);

        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            teacher = Teacher.builder()
                    .teacherId(teacherId)
                    .fullName(teacherName)
                    .username(generatedUsername)
                    .password(passwordEncoder.encode(generatedPassword))
                    .role("TEACHER")
                    .semesterStartDate(LocalDate.of(2026, 8, 10))
                    .build();
        } else {
            teacher.setFullName(teacherName);
            if (teacher.getUsername() == null || teacher.getUsername().isBlank()) {
                teacher.setUsername(generatedUsername);
            }
            if (teacher.getPassword() == null || teacher.getPassword().isBlank()) {
                teacher.setPassword(passwordEncoder.encode(generatedPassword));
            }
        }
        teacherRepository.save(teacher);

        List<TeacherAccountDTO.SubjectAssignment> savedSubjects = new ArrayList<>();
        for (TeacherAccountDTO.SubjectAssignment sub : subjectList) {
            if (sub.getCourseCode() == null || sub.getCourseCode().isBlank()) continue;
            String courseCode = sub.getCourseCode().trim().toUpperCase(Locale.ROOT);
            String cohort = (sub.getCohort() == null || sub.getCohort().isBlank()) ? "*" : sub.getCohort().trim();

            TeacherSubject ts = (sub.getId() != null)
                    ? teacherSubjectRepository.findById(sub.getId()).orElse(new TeacherSubject())
                    : teacherSubjectRepository.findBySchoolCodeAndTeacherIdIgnoreCaseAndCourseCodeIgnoreCaseAndCohortIgnoreCase(
                            schoolCode, teacherId, courseCode, cohort).orElse(new TeacherSubject());

            ts.setSchoolCode(schoolCode);
            ts.setTeacherId(teacherId);
            ts.setTeacherName(teacherName);
            ts.setCourseCode(courseCode);
            ts.setCourseName(sub.getCourseName() != null ? sub.getCourseName().trim() : "");
            ts.setCohort(cohort);
            ts.setTheoryWeight(sub.getTheoryWeight() != null ? sub.getTheoryWeight() : 1);
            ts.setPracticeWeight(sub.getPracticeWeight() != null ? sub.getPracticeWeight() : 1);

            TeacherSubject savedTs = teacherSubjectRepository.save(ts);
            savedSubjects.add(TeacherAccountDTO.SubjectAssignment.builder()
                    .id(savedTs.getId())
                    .courseCode(savedTs.getCourseCode())
                    .courseName(savedTs.getCourseName())
                    .cohort(savedTs.getCohort())
                    .theoryWeight(savedTs.getTheoryWeight())
                    .practiceWeight(savedTs.getPracticeWeight())
                    .build());
        }

        return ResponseEntity.ok(TeacherAccountDTO.builder()
                .schoolCode(schoolCode)
                .teacherId(teacherId)
                .teacherName(teacherName)
                .username(teacher.getUsername())
                .defaultPassword(generatedPassword)
                .subjects(savedSubjects)
                .build());
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