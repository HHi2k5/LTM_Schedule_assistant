package com.controller;

import com.dto.CatalogDTO.*;
import com.service.CatalogService;
import com.service.LegacyCatalogService;
import com.dto.CatalogImportDTO.LegacySchool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/institutions")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogs;
    private final LegacyCatalogService legacy;

    @GetMapping("/legacy")
    public List<LegacySchool> legacy() { return legacy.preview(); }

    @GetMapping
    public List<InstitutionView> institutions() { return catalogs.listInstitutions(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public InstitutionView createInstitution(@RequestBody InstitutionInput input) { return catalogs.createInstitution(input); }

    @PutMapping("/{schoolCode}")
    public InstitutionView updateInstitution(@PathVariable String schoolCode, @RequestBody InstitutionInput input) {
        return catalogs.updateInstitution(schoolCode, input);
    }

    @GetMapping("/{schoolCode}/departments")
    public List<DepartmentView> departments(@PathVariable String schoolCode) { return catalogs.listDepartments(schoolCode); }

    @PostMapping("/{schoolCode}/departments") @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView createDepartment(@PathVariable String schoolCode, @RequestBody DepartmentInput input) {
        return catalogs.createDepartment(schoolCode, input);
    }

    @PutMapping("/{schoolCode}/departments/{id}")
    public DepartmentView updateDepartment(@PathVariable String schoolCode, @PathVariable Long id, @RequestBody DepartmentInput input) {
        return catalogs.updateDepartment(schoolCode, id, input);
    }

    @GetMapping("/{schoolCode}/lecturers")
    public List<LecturerView> lecturers(@PathVariable String schoolCode) { return catalogs.listLecturers(schoolCode); }

    @PostMapping("/{schoolCode}/lecturer-accounts")
    public AccountBatchResult lecturerAccounts(@PathVariable String schoolCode, @RequestBody AccountBatch input) {
        return catalogs.createMissingAccounts(schoolCode, input);
    }

    @PostMapping("/{schoolCode}/lecturers") @ResponseStatus(HttpStatus.CREATED)
    public LecturerView createLecturer(@PathVariable String schoolCode, @RequestBody LecturerInput input) {
        return catalogs.createLecturer(schoolCode, input);
    }

    @PutMapping("/{schoolCode}/lecturers/{id}")
    public LecturerView updateLecturer(@PathVariable String schoolCode, @PathVariable Long id, @RequestBody LecturerInput input) {
        return catalogs.updateLecturer(schoolCode, id, input);
    }

    @GetMapping("/{schoolCode}/lecturers/{lecturerId}/assignments")
    public List<AssignmentView> assignments(@PathVariable String schoolCode, @PathVariable Long lecturerId) {
        return catalogs.listAssignments(schoolCode, lecturerId);
    }

    @PostMapping("/{schoolCode}/lecturers/{lecturerId}/assignments") @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView createAssignment(@PathVariable String schoolCode, @PathVariable Long lecturerId, @RequestBody AssignmentInput input) {
        return catalogs.createAssignment(schoolCode, lecturerId, input);
    }

    @PutMapping("/{schoolCode}/lecturers/{lecturerId}/assignments/{id}")
    public AssignmentView updateAssignment(@PathVariable String schoolCode, @PathVariable Long lecturerId,
                                          @PathVariable Long id, @RequestBody AssignmentInput input) {
        return catalogs.updateAssignment(schoolCode, lecturerId, id, input);
    }

    @GetMapping("/{schoolCode}/rooms")
    public List<RoomView> rooms(@PathVariable String schoolCode) { return catalogs.listRooms(schoolCode); }

    @PostMapping("/{schoolCode}/rooms") @ResponseStatus(HttpStatus.CREATED)
    public RoomView createRoom(@PathVariable String schoolCode, @RequestBody RoomInput input) { return catalogs.createRoom(schoolCode, input); }

    @PutMapping("/{schoolCode}/rooms/{id}")
    public RoomView updateRoom(@PathVariable String schoolCode, @PathVariable Long id, @RequestBody RoomInput input) {
        return catalogs.updateRoom(schoolCode, id, input);
    }
}
