package com.controller;

import com.dto.AssignmentView;
import com.service.TeacherAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Trưởng bộ môn (hoặc quản trị khoa) phân công giảng viên vào các lớp đã xếp phòng. */
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

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@RequestBody AssignmentView.AssignRequest request) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", assignments.assign(request)));
    }

    @PostMapping("/auto")
    public ResponseEntity<AssignmentView.AutoResult> autoAssign(@RequestBody AssignmentView.AutoRequest request) {
        return ResponseEntity.ok(assignments.autoAssign(request));
    }
}
