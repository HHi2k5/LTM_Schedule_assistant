package com.controller;

import com.dto.AssignmentView;
import com.service.TeacherAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Trưởng bộ môn phân công giảng viên và bàn giao hai chiều với Admin. */
@RestController
@RequestMapping("/api/schedule/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final TeacherAssignmentService assignments;

    @GetMapping
    public ResponseEntity<AssignmentView.Overview> overview(@RequestParam(defaultValue = "PTIT") String schoolId,
                                                            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(assignments.overview(schoolId, departmentId));
    }

    @PostMapping("/dispatch")
    public ResponseEntity<?> dispatch(@RequestBody AssignmentView.WorkflowRequest request) {
        String schoolId = request == null ? "PTIT" : request.schoolId();
        Long deptId = request == null ? null : request.departmentId();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", assignments.dispatchToDepartments(schoolId, deptId)));
    }

    @PostMapping("/reopen")
    public ResponseEntity<?> reopen(@RequestBody AssignmentView.WorkflowRequest request) {
        String schoolId = request == null ? "PTIT" : request.schoolId();
        Long deptId = request == null ? null : request.departmentId();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", assignments.reopenForDepartment(schoolId, deptId)));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody(required = false) AssignmentView.WorkflowRequest request) {
        String schoolId = request == null ? "PTIT" : request.schoolId();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", assignments.submitToAdmin(schoolId)));
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@RequestBody AssignmentView.AssignRequest request) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", assignments.assign(request)));
    }

    @PostMapping("/assign-batch")
    public ResponseEntity<AssignmentView.AutoResult> assignBatch(@RequestBody AssignmentView.BatchAssignRequest request) {
        return ResponseEntity.ok(assignments.assignBatch(request));
    }

    @PostMapping("/auto")
    public ResponseEntity<AssignmentView.AutoResult> autoAssign(@RequestBody AssignmentView.AutoRequest request) {
        return ResponseEntity.ok(assignments.autoAssign(request));
    }

    @PostMapping("/auto-visiting")
    public ResponseEntity<AssignmentView.AutoResult> autoAssignVisiting(
            @RequestParam(defaultValue = "PTIT") String schoolId,
            @RequestParam String teacherId) {
        return ResponseEntity.ok(assignments.autoAssignVisiting(schoolId, teacherId));
    }

    @GetMapping("/visiting/{teacherId}")
    public ResponseEntity<AssignmentView.VisitingProfile> visitingProfile(
            @PathVariable String teacherId,
            @RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(assignments.visitingProfile(schoolId, teacherId));
    }

    @PostMapping("/visiting/busy-days")
    public ResponseEntity<AssignmentView.VisitingProfile> updateBusyDays(@RequestBody AssignmentView.BusyDaysRequest request) {
        return ResponseEntity.ok(assignments.updateBusyDays(request));
    }

    @PostMapping("/visiting/busy-dates")
    public ResponseEntity<AssignmentView.VisitingProfile> addBusyDates(@RequestBody AssignmentView.BusyDatesRequest request) {
        return ResponseEntity.ok(assignments.addBusyDates(request));
    }

    @DeleteMapping("/visiting/busy/{id}")
    public ResponseEntity<?> deleteBusyEntry(@PathVariable Long id) {
        assignments.deleteBusyEntry(id);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Đã xóa mục lịch bận."));
    }
}
