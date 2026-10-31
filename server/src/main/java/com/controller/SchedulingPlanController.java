package com.controller;

import com.dto.SchedulingPlanDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.service.SchedulingPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/admin/scheduling-plans") @RequiredArgsConstructor
public class SchedulingPlanController {
    private final SchedulingPlanService plans;
    private final ObjectMapper json;
    private final com.service.FacultyBoardService board;
    private final com.service.VisitingLecturerService visiting;
    private final com.service.ScheduleAdjustmentService adjustments;
    @GetMapping("/{id}/adjustment-options") public com.dto.ScheduleAdjustmentDTO.Options adjustmentOptions(@PathVariable Long id) throws Exception { return adjustments.options(id); }
    @GetMapping("/{id}/adjustment-entries") public EntryPage editable(@PathVariable Long id, @RequestParam Long baseVersionId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) { return adjustments.editable(id, baseVersionId, page, q); }
    @PostMapping("/{id}/adjustments/preview") public com.dto.ScheduleAdjustmentDTO.Preview previewAdjustment(@PathVariable Long id, @RequestBody com.dto.ScheduleAdjustmentDTO.Input input) throws Exception { return adjustments.preview(id, input); }
    @PostMapping("/{id}/adjustments/commit") public Detail commitAdjustment(@PathVariable Long id, @RequestBody com.dto.ScheduleAdjustmentDTO.Commit input) throws Exception { return adjustments.commit(id, input); }
    @GetMapping("/{id}/changes") public com.dto.ScheduleAdjustmentDTO.LogPage changes(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) { return plans.changeLog(id, page); }
    @GetMapping("/{id}/visiting-options") public com.dto.VisitingLecturerDTO.Options visitingOptions(@PathVariable Long id) throws Exception { return visiting.options(id); }
    @PostMapping("/{id}/visiting-lecturers") @ResponseStatus(HttpStatus.CREATED)
    public com.dto.CatalogDTO.LecturerView createVisiting(@PathVariable Long id, @RequestBody com.dto.VisitingLecturerDTO.Profile input) throws Exception { return visiting.createProfile(id, input); }
    @PostMapping("/{id}/visiting-lecturers/preview")
    public com.dto.VisitingLecturerDTO.Preview previewVisiting(@PathVariable Long id, @RequestBody com.dto.VisitingLecturerDTO.Input input) throws Exception { return visiting.preview(id, input); }
    @PostMapping("/{id}/visiting-lecturers/commit")
    public Detail commitVisiting(@PathVariable Long id, @RequestBody com.dto.VisitingLecturerDTO.Commit input) throws Exception { return visiting.commit(id, input); }
    @GetMapping public List<Summary> list(@RequestParam(required = false) String schoolCode) { return plans.list(schoolCode); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Detail create(@RequestBody Create input) throws Exception { return plans.create(input); }
    @GetMapping("/{id}") public Detail get(@PathVariable Long id) throws Exception { return plans.get(id); }
    @GetMapping("/{id}/options") public Options options(@PathVariable Long id) throws Exception { return plans.options(id); }
    @PostMapping(value = "/{id}/generate-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Detail generate(@PathVariable Long id, @RequestParam long revision, @RequestParam MultipartFile planFile, @RequestParam String configuration) throws Exception {
        Configuration parsed;
        try { parsed = json.readValue(configuration, Configuration.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Cấu hình danh mục không phải JSON hợp lệ."); }
        return plans.generate(id, revision, planFile, parsed);
    }
    @PostMapping("/{id}/validate") public List<Conflict> validate(@PathVariable Long id, @RequestBody Revision revision) throws Exception { return plans.validate(id, revision.revision()); }
    @PostMapping("/{id}/confirm") public Detail confirm(@PathVariable Long id, @RequestBody Revision revision) throws Exception { return plans.confirm(id, revision.revision()); }
    @GetMapping("/{id}/versions/{versionId}") public Version version(@PathVariable Long id, @PathVariable Long versionId) throws Exception { return plans.version(id, versionId); }
    @GetMapping("/{id}/versions/{versionId}/entries") public EntryPage entries(@PathVariable Long id, @PathVariable Long versionId, @RequestParam(defaultValue = "0") int page) { return plans.entryPage(id, versionId, page); }
    @GetMapping("/{id}/versions/{versionId}/overview")
    public com.dto.FacultyBoardDTO.Board overview(@PathVariable Long id, @PathVariable Long versionId,
            @RequestParam(defaultValue = "1") Integer week, @RequestParam(defaultValue = "calendar") String view,
            @RequestParam(defaultValue = "lecturer") String groupBy, @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(required = false) Long lecturerId, @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) String cohort, @RequestParam(required = false) String classId,
            @RequestParam(required = false) String roomCode, @RequestParam(required = false) String sessionType,
            @RequestParam(required = false) String lecturerType, @RequestParam(defaultValue = "false") Boolean conflictsOnly,
            @RequestParam(required = false) String q) throws Exception {
        return board.overview(id, versionId, new com.dto.FacultyBoardDTO.Query(week, view, groupBy, page, lecturerId, courseCode, cohort, classId, roomCode, sessionType, lecturerType, conflictsOnly, q));
    }
}
