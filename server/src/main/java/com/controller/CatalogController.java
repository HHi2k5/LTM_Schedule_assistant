package com.controller;

import com.dto.DepartmentDTO;
import com.model.Department;
import com.model.RoomType;
import com.model.Subject;
import com.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Danh mục bộ môn, môn học và loại phòng của khoa. */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalog;

    private static ResponseEntity<Map<String, String>> done(String message) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", message));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDTO>> departments(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(catalog.listDepartments(schoolId));
    }

    @PostMapping("/departments")
    public ResponseEntity<Department> saveDepartment(@RequestBody Department payload) {
        return ResponseEntity.ok(catalog.saveDepartment(payload));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<?> deleteDepartment(@PathVariable Long id) {
        catalog.deleteDepartment(id);
        return done("Đã xóa bộ môn.");
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<Subject>> subjects(@RequestParam(defaultValue = "PTIT") String schoolId,
                                                  @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(catalog.listSubjects(schoolId, departmentId));
    }

    @GetMapping("/subjects/next-code")
    public ResponseEntity<Map<String, String>> nextSubjectCode(@RequestParam Long departmentId) {
        return ResponseEntity.ok(Map.of("courseCode", catalog.nextSubjectCode(departmentId)));
    }

    @PostMapping("/subjects")
    public ResponseEntity<Subject> saveSubject(@RequestBody Subject payload) {
        return ResponseEntity.ok(catalog.saveSubject(payload));
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        catalog.deleteSubject(id);
        return done("Đã xóa môn học.");
    }

    @GetMapping("/room-types")
    public ResponseEntity<List<RoomType>> roomTypes(@RequestParam(defaultValue = "PTIT") String schoolId) {
        return ResponseEntity.ok(catalog.listRoomTypes(schoolId));
    }

    @PostMapping("/room-types")
    public ResponseEntity<RoomType> saveRoomType(@RequestBody RoomType payload) {
        return ResponseEntity.ok(catalog.saveRoomType(payload));
    }

    @DeleteMapping("/room-types/{id}")
    public ResponseEntity<?> deleteRoomType(@PathVariable Long id) {
        catalog.deleteRoomType(id);
        return done("Đã xóa loại phòng.");
    }
}
