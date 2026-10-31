package com.service;

import com.dto.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.ScheduleAdjustmentDTO.*;
import com.model.*;
import com.repository.*;
import com.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ScheduleAdjustmentService {
    private final SchedulingPlanRepository plans;
    private final SchedulingPlanVersionRepository versions;
    private final SchedulingEntryRepository entries;
    private final InstitutionLecturerRepository lecturers;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final CatalogSchedulingService catalog;
    private final VisitingLecturerService visiting;
    private final SchedulingPlanService storage;
    private final SchedulingPlanValidation validation;
    private final PartialScheduleEngine engine;
    private final ScheduleWriteLock lock;
    private final ObjectMapper json;

    public ScheduleAdjustmentDTO.Options options(Long id) throws Exception {
        SchedulingPlanService.admin(); var plan = plan(id); var version = draft(plan);
        var source = catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId()); var classes = visiting.options(id);
        return new ScheduleAdjustmentDTO.Options(plan.getRevision(), version.getId(), source.snapshot(), source.lecturers().stream().filter(LecturerOption::eligible).map(LecturerOption::lecturer).toList(),
                source.assignments(), source.rooms(), classes.periods(), classes.classes());
    }
    public SchedulingPlanDTO.EntryPage editable(Long id, Long baseVersionId, int page, String search) {
        SchedulingPlanService.admin(); var plan = plan(id); if (!draft(plan).getId().equals(baseVersionId)) throw stale(id);
        if (page < 0 || search != null && search.length() > 200) throw new IllegalArgumentException("Trang hoặc tìm kiếm không hợp lệ.");
        String q = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        // Escape LIKE wildcards so user text is a literal search.
        q = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var data = entries.searchEditable(baseVersionId, q, PageRequest.of(page, 25));
        return new SchedulingPlanDTO.EntryPage(data.getContent().stream().map(e -> new SchedulingPlanDTO.Entry(e.getId(), e.getLecturer().getId(), e.getLecturer().getLecturerCode(), e.getLecturerName(), e.getDemand().getCourseCode(), e.getCourseName(), e.getDemand().getCohort(),
                e.getClassGroup(), e.getSessionType(), e.getStudentCount(), e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getStartPeriod(), e.getNumPeriods(), e.getEntryKey(), e.isLocked())).toList(), page, data.getTotalPages(), data.getTotalElements());
    }
    public Preview preview(Long id, Input input) throws Exception { return build(id, input).preview(); }
    @Transactional(rollbackFor = Exception.class)
    public SchedulingPlanDTO.Detail commit(Long id, Commit command) throws Exception {
        SchedulingPlanService.admin(); lock.acquire();
        if (command == null || command.previewToken() == null) throw new IllegalArgumentException("Cần xem trước điều chỉnh trước khi lưu.");
        var proposal = build(id, command.input());
        if (!MessageDigest.isEqual(proposal.preview().previewToken().getBytes(StandardCharsets.UTF_8), command.previewToken().getBytes(StandardCharsets.UTF_8))) throw stale(id);
        if (!proposal.preview().canApply()) throw new IllegalArgumentException("Đề xuất không có thay đổi hoặc chưa tìm được phương án; bản nháp cũ được giữ.");
        var change = new ChangeInfo(command.input().action(), command.input().baseVersionId(), command.input().restoreVersionId(), proposal.preview().changes().stream().map(Delta::entryKey).toList(),
                reason(command.input()), SchedulingPlanService.admin(), Instant.now());
        var state = proposal.after();
        return storage.storeAdjustedDraft(proposal.plan(), state.request, state.config, state.result, state.rows, state.visitingChange, change, state.customized, proposal.preview().changes().size());
    }
    private record Proposal(SchedulingPlan plan, State after, Preview preview) {}
    private static class State {
        SchoolWideScheduleRequest request; Configuration config; AutoScheduleResponse result; List<SchedulingEntry> rows;
        VisitingLecturerDTO.ChangeInfo visitingChange; boolean customized;
    }
    private State load(SchedulingPlanVersion version) throws Exception {
        var state = new State(); state.request = json.readValue(version.getRequestJson(), SchoolWideScheduleRequest.class); state.config = json.readValue(version.getConfigurationJson(), Configuration.class);
        state.result = json.readValue(version.getResultJson(), AutoScheduleResponse.class); state.rows = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(version.getId()).stream().map(ScheduleAdjustmentService::copy).collect(Collectors.toCollection(ArrayList::new));
        VisitingLecturerService.enrichWorkloads(state.request, state.result, state.rows);
        state.visitingChange = version.getVisitingChangeJson() == null ? null : json.readValue(version.getVisitingChangeJson(), VisitingLecturerDTO.ChangeInfo.class);
        state.customized = version.isCustomized() || state.visitingChange != null || state.rows.stream().anyMatch(SchedulingEntry::isLocked); return state;
    }
    private Proposal build(Long id, Input input) throws Exception {
        String actor = SchedulingPlanService.admin(); var plan = plan(id);
        if (input == null || input.revision() == null || input.baseVersionId() == null) throw new IllegalArgumentException("Cần revision và phiên bản cơ sở.");
        if (input.revision() != plan.getRevision() || !draft(plan).getId().equals(input.baseVersionId())) throw stale(id);
        if (!Set.of("MOVE", "ADD", "DELETE", "LOCK", "REASSIGN", "AUTO", "RESTORE").contains(input.action() == null ? "" : input.action())) throw new IllegalArgumentException("Thao tác điều chỉnh không hợp lệ.");
        reason(input); var source = catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId());
        if (!source.snapshot().equals(input.snapshot())) throw stale(id);
        var before = load(draft(plan)); var after = load(draft(plan));
        var sourceIds = before.rows.stream().collect(Collectors.toMap(SchedulingEntry::getId, Function.identity()));
        Set<Long> selectedIds = ids(input.entryIds()); Set<String> selectedClasses = classes(input.classIds());
        var workloads = after.result.getClassWorkloads().stream().collect(Collectors.toMap(ClassWorkloadDTO::getClassId, Function.identity()));
        if (!sourceIds.keySet().containsAll(selectedIds) || !workloads.keySet().containsAll(selectedClasses)) throw new IllegalArgumentException("Phạm vi phải thuộc bản nháp đang mở.");
        boolean solved = true; String message = "Đề xuất đã giữ các buổi ngoài phạm vi.";
        Function<String, String> keyFactory = seed -> "ADJ_" + hash(input.baseVersionId() + ":" + seed + ":" + uncheckedJson(input)).substring(0, 36);
        switch (input.action()) {
            case "MOVE" -> {
                var edits = edits(input); var seen = new HashSet<Long>();
                for (var edit : edits) {
                    if (edit.entryId() == null || !seen.add(edit.entryId()) || !sourceIds.containsKey(edit.entryId())) throw new IllegalArgumentException("Buổi sửa phải duy nhất và thuộc bản nháp.");
                    var row = after.rows.stream().filter(e -> e.getId().equals(edit.entryId())).findFirst().orElseThrow(); unlocked(row);
                    if (edit.classId() != null && !edit.classId().equals(row.getScheduleClassId())) throw new IllegalArgumentException("Sửa buổi không đổi lớp/nhóm gốc.");
                    move(plan, after.request, row, edit, source);
                }
            }
            case "ADD" -> {
                long temp = -1; int sequence = 0;
                for (var edit : edits(input)) {
                    if (edit.entryId() != null || !workloads.containsKey(edit.classId())) throw new IllegalArgumentException("Bổ sung buổi cần lớp/nhóm hiện có và không gửi ID buổi cũ.");
                    var w = workloads.get(edit.classId()); var identity = after.result.getCatalogContext().lecturers().stream().filter(a -> Objects.equals(a.teacherId(), w.getTeacherId())).findFirst().orElse(null);
                    if (identity == null) { if (input.lecturerId() == null) throw new IllegalArgumentException("Chọn GV cho lớp/nhóm trước khi thêm buổi."); reassign(after, Set.of(w.getClassId()), input.lecturerId(), source, plan); }
                    else if (input.lecturerId() != null && !identity.lecturerId().equals(input.lecturerId())) throw new IllegalArgumentException("Đổi GV theo toàn lớp/nhóm trước khi thêm buổi để giữ đúng phân công.");
                    identity = after.result.getCatalogContext().lecturers().stream().filter(a -> Objects.equals(a.teacherId(), w.getTeacherId())).findFirst().orElseThrow();
                    var eligible = identity; if (source.lecturers().stream().noneMatch(o -> o.eligible() && o.lecturer().id().equals(eligible.lecturerId()))) throw new IllegalArgumentException("GV của lớp không còn đủ điều kiện.");
                    var c = after.request.getCourses().get(w.getDemandIndex() - 1); var row = new SchedulingEntry(); row.setId(temp--); row.setEntryKey(keyFactory.apply(w.getClassId() + ":add:" + sequence++));
                    var demand = new SchedulingDemand(); demand.setDemandIndex(w.getDemandIndex()); demand.setCourseCode(w.getCourseCode()); demand.setCohort(w.getCohort()); row.setDemand(demand);
                    row.setScheduleClassId(w.getClassId()); row.setScheduleParentId(w.getParentClassId()); row.setClassGroup(w.getClassGroup()); row.setSessionType(w.getSessionType()); row.setStudentCount(w.getStudentCount()); row.setLecturer(lecturers.getReferenceById(identity.lecturerId()));
                    row.setLecturerName(c.getTeachers().stream().filter(t -> t.getTeacherId().equals(w.getTeacherId())).map(TeacherCandidateDTO::getTeacherName).findFirst().orElse(row.getLecturer().getFullName()));
                    row.setCourseName(CatalogService.text(("PRACTICE".equals(w.getSessionType()) ? "[TH] " : "[LT] ") + c.getCourseName() + " (Khóa " + c.getCohort() + " - " + w.getClassGroup() + ")", 255, "Tên buổi"));
                    move(plan, after.request, row, edit, source); after.rows.add(row);
                }
            }
            case "DELETE" -> {
                if (selectedIds.isEmpty() || !selectedClasses.isEmpty()) throw new IllegalArgumentException("Chọn buổi cần bỏ; nhu cầu của lớp vẫn được giữ.");
                after.rows.stream().filter(e -> selectedIds.contains(e.getId())).forEach(ScheduleAdjustmentService::unlocked); after.rows.removeIf(e -> selectedIds.contains(e.getId()));
            }
            case "LOCK" -> {
                if (input.locked() == null || selectedIds.isEmpty() && selectedClasses.isEmpty()) throw new IllegalArgumentException("Chọn buổi/lớp và trạng thái khóa.");
                after.rows.stream().filter(e -> selectedIds.contains(e.getId()) || selectedClasses.contains(e.getScheduleClassId())).forEach(e -> e.setLocked(input.locked()));
            }
            case "REASSIGN" -> {
                if (selectedClasses.isEmpty() || !selectedIds.isEmpty() || input.lecturerId() == null) throw new IllegalArgumentException("Đổi GV cần chọn toàn lớp/nhóm và giảng viên.");
                reassign(after, selectedClasses, input.lecturerId(), source, plan);
            }
            case "AUTO" -> {
                if (selectedIds.isEmpty() && selectedClasses.isEmpty()) throw new IllegalArgumentException("Chọn buổi hoặc lớp/nhóm cần xếp lại.");
                var chosenRooms = input.roomIds() == null ? after.config.roomIds() : input.roomIds(); var allowedRooms = source.rooms().stream().map(com.dto.CatalogDTO.RoomView::id).collect(Collectors.toSet());
                if (chosenRooms.isEmpty() || chosenRooms.size() > 10000 || new HashSet<>(chosenRooms).size() != chosenRooms.size() || !allowedRooms.containsAll(chosenRooms)) throw new IllegalArgumentException("Chọn tập phòng đang hoạt động trong trường.");
                var from = input.fromDate() == null ? plan.getSemesterStartDate() : input.fromDate(); var to = input.toDate() == null ? after.request.getCourses().stream().map(CourseDemandDTO::getStudyEndDate).max(LocalDate::compareTo).orElseThrow() : input.toDate();
                date(plan, from); date(plan, to); if (to.isBefore(from)) throw new IllegalArgumentException("Khoảng xếp lại không hợp lệ.");
                var scopeClasses = new HashSet<>(selectedClasses); before.rows.stream().filter(e -> selectedIds.contains(e.getId()) && !e.isLocked()).forEach(e -> scopeClasses.add(e.getScheduleClassId()));
                for (var classId : scopeClasses) {
                    var w = workloads.get(classId); var identity = after.result.getCatalogContext().lecturers().stream().filter(a -> Objects.equals(a.teacherId(), w.getTeacherId())).findFirst().orElse(null);
                    if (identity == null || source.lecturers().stream().noneMatch(o -> o.eligible() && o.lecturer().id().equals(identity.lecturerId())) || source.assignments().stream().noneMatch(a -> a.lecturerId().equals(identity.lecturerId()) && a.courseCode().equalsIgnoreCase(w.getCourseCode()) && a.cohort().equalsIgnoreCase(w.getCohort()))) throw new IllegalArgumentException("Lớp/nhóm cần GV và phân công đang hoạt động trước khi xếp lại.");
                }
                var activeRooms = rooms.findBySchoolCodeOrderByCapacityAsc(plan.getInstitution().getSchoolCode()).stream().filter(r -> chosenRooms.contains(r.getId())).toList();
                var outcome = engine.arrange(plan, after.request, after.result, after.rows, selectedClasses, selectedIds, activeRooms, from, to, keyFactory);
                solved = outcome.success(); after.rows = new ArrayList<>(outcome.rows()); message = outcome.message();
            }
            case "RESTORE" -> {
                if (input.restoreVersionId() == null || input.restoreVersionId().equals(input.baseVersionId())) throw new IllegalArgumentException("Chọn phiên bản lịch sử khác bản nháp hiện tại.");
                var target = versions.findByIdAndPlan_Id(input.restoreVersionId(), id).orElseThrow(() -> new ResourceNotFoundException("Phiên bản khôi phục không thuộc bản lịch.")); after = load(target);
                var restored = after.rows.stream().collect(Collectors.toMap(SchedulingEntry::getEntryKey, Function.identity()));
                if (!input.overrideLocks() && before.rows.stream().anyMatch(e -> e.isLocked() && (!restored.containsKey(e.getEntryKey()) || !session(e, false).equals(session(restored.get(e.getEntryKey()), false)))))
                    throw new IllegalArgumentException("Khôi phục sẽ thay buổi khóa. Mở khóa trước hoặc chọn rõ khôi phục cả buổi đang khóa.");
                message = "Khôi phục toàn bộ nội dung phiên bản đã chọn vào nháp mới, gồm nhu cầu, lịch bận và trạng thái khóa.";
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ.");
        }
        if (!"RESTORE".equals(input.action())) after.customized = true;
        validateRows(after.rows);
        var roomIds = new LinkedHashSet<>(after.config.roomIds()); after.rows.forEach(e -> roomIds.add(e.getRoom().getId()));
        after.config = new Configuration(source.snapshot(), after.config.lecturerIds(), List.copyOf(roomIds), after.config.weights(), after.config.periodChoiceId(), false);
        var context = after.result.getCatalogContext();
        after.result = VisitingResultBuilder.rebuild(after.request, after.result, after.rows, after.result.getClassWorkloads(), new RunContext(context.schoolCode(), source.snapshot(), after.config.roomIds(), context.lecturers(), context.periodChoiceId()));
        after.result.setMessage("Bản nháp điều chỉnh chưa thay lịch cá nhân.");
        var changes = deltas(before.rows, after.rows); var removedBusy = difference(PublishedBusyReservations.own(before.request), PublishedBusyReservations.own(after.request)); var addedBusy = difference(PublishedBusyReservations.own(after.request), PublishedBusyReservations.own(before.request));
        if (changes.size() > 2000 || removedBusy.size() + addedBusy.size() > 2000) throw new IllegalArgumentException("Đề xuất vượt 2000 buổi hoặc khoảng bận thay đổi; thu hẹp phạm vi.");
        var trial = new SchedulingPlanVersion(); trial.setComplete(after.result.isComplete()); var conflicts = validation.check(plan, trial, after.request, after.result, after.rows);
        boolean restoredSettings = "RESTORE".equals(input.action()) && (before.customized != after.customized
                || !Objects.equals(before.visitingChange, after.visitingChange)
                || !sameSettings(before.config, after.config));
        boolean configChanged = restoredSettings || !uncheckedJson(before.request).equals(uncheckedJson(after.request));
        boolean changed = !changes.isEmpty() || !removedBusy.isEmpty() || !addedBusy.isEmpty() || configChanged;
        String status = !solved ? "NO_SOLUTION" : changed ? "READY" : "NO_CHANGE";
        if (!solved) { after = before; changes = List.of(); removedBusy = List.of(); addedBusy = List.of(); configChanged = false; }
        if (solved && !changed) message = "Nội dung đã trùng với bản nháp hiện tại; không tạo phiên bản mới.";
        String token = hash(actor + "\n" + uncheckedJson(input) + "\n" + uncheckedJson(after.request) + "\n" + uncheckedJson(after.result) + "\n" + uncheckedJson(changes) + "\n" + uncheckedJson(conflicts) + "\n" + status);
        var preview = new Preview(token, plan.getRevision(), input.baseVersionId(), input.action(), status, message, solved && changed, after.result.isComplete(), after.result.isComplete() && conflicts.isEmpty(),
                before.rows.size() - (int) changes.stream().filter(d -> d.before() != null).count(), (int) after.rows.stream().filter(SchedulingEntry::isLocked).count(),
                before.result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getRequiredPeriods).sum(), after.result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getRequiredPeriods).sum(), after.result.getClassWorkloads().stream().mapToInt(ClassWorkloadDTO::getMissingPeriods).sum(), configChanged,
                changes, removedBusy, addedBusy, conflicts);
        return new Proposal(plan, after, preview);
    }
    private void move(SchedulingPlan plan, SchoolWideScheduleRequest request, SchedulingEntry row, Edit edit, com.dto.CatalogSchedulingDTO.Options source) {
        var date = edit.date() == null ? row.getLessonDate() : edit.date(); date(plan, date);
        var roomId = edit.roomId() == null && row.getRoom() != null ? row.getRoom().getId() : edit.roomId();
        if (roomId == null || source.rooms().stream().noneMatch(r -> r.id().equals(roomId))) throw new IllegalArgumentException("Phòng phải đang hoạt động và thuộc trường.");
        int start = edit.startPeriod() == null ? row.getStartPeriod() : edit.startPeriod(), count = edit.numPeriods() == null ? row.getNumPeriods() : edit.numPeriods();
        var periods = ScheduleValidation.periods(request.getTargetSchool().getPeriods()); if (!ScheduleValidation.hasPeriods(periods, start, count)) throw new IllegalArgumentException("Tiết không thuộc bảng giờ của bản lịch.");
        row.setLessonDate(date); row.setRoom(rooms.getReferenceById(roomId)); row.setRoomCode(row.getRoom().getRoomCode()); row.setStartPeriod(start); row.setNumPeriods(count); row.setStartTime(periods.get(start).getStart()); row.setEndTime(periods.get(start + count - 1).getEnd());
    }
    private void reassign(State state, Set<String> classes, Long id, com.dto.CatalogSchedulingDTO.Options source, SchedulingPlan plan) {
        var profile = source.lecturers().stream().filter(o -> o.eligible() && o.lecturer().id().equals(id)).map(LecturerOption::lecturer).findFirst().orElseThrow(() -> new IllegalArgumentException("Chọn GV đủ điều kiện trong khoa."));
        if (state.rows.stream().anyMatch(e -> classes.contains(e.getScheduleClassId()) && e.isLocked() && !e.getLecturer().getId().equals(id))) throw new IllegalArgumentException("Lớp/nhóm có buổi khóa; mở khóa trước khi đổi GV.");
        var identities = new ArrayList<>(state.result.getCatalogContext().lecturers()); var old = identities.stream().filter(a -> a.lecturerId().equals(id)).findFirst().orElse(null);
        if (old != null && !LecturerAccountService.compatible(old, profile)) throw new IllegalArgumentException("Danh tính GV đã đổi; cần đối chiếu trước.");
        String key = old != null ? old.teacherId() : profile.accountTeacherId() != null ? profile.accountTeacherId() : profile.scheduleTeacherId() != null ? profile.scheduleTeacherId() : virtual(plan, id);
        identities.removeIf(a -> a.lecturerId().equals(id)); identities.add(new LecturerIdentity(id, profile.lecturerCode(), key, profile.accountTeacherId(), profile.lecturerType().name()));
        for (var w : state.result.getClassWorkloads()) if (classes.contains(w.getClassId())) {
            var a = source.assignments().stream().filter(candidate -> candidate.lecturerId().equals(id) && candidate.courseCode().equalsIgnoreCase(w.getCourseCode()) && candidate.cohort().equalsIgnoreCase(w.getCohort())).findFirst().orElseThrow(() -> new IllegalArgumentException("GV chưa có phân công môn–khóa."));
            if (("PRACTICE".equals(w.getSessionType()) ? a.practiceWeight() : a.theoryWeight()) <= 0) throw new IllegalArgumentException("GV cần trọng số dương cho đúng LT/TH.");
            w.setTeacherId(key); var c = state.request.getCourses().get(w.getDemandIndex() - 1); var candidates = new ArrayList<>(c.getTeachers()); candidates.removeIf(t -> id.equals(t.getCatalogLecturerId()));
            candidates.add(TeacherCandidateDTO.builder().catalogLecturerId(id).lecturerCode(profile.lecturerCode()).teacherId(key).teacherName(profile.fullName()).theoryWeight(a.theoryWeight()).practiceWeight(a.practiceWeight()).build()); c.setTeachers(candidates);
        }
        state.rows.stream().filter(e -> classes.contains(e.getScheduleClassId()) && !e.isLocked()).forEach(e -> { e.setLecturer(lecturers.getReferenceById(id)); e.setLecturerName(profile.fullName()); });
        var actorIds = new LinkedHashSet<>(state.config.lecturerIds()); actorIds.add(id); var context = state.result.getCatalogContext();
        state.config = new Configuration(source.snapshot(), List.copyOf(actorIds), state.config.roomIds(), state.config.weights(), state.config.periodChoiceId(), false);
        state.result.setCatalogContext(new RunContext(context.schoolCode(), source.snapshot(), context.roomIds(), identities, context.periodChoiceId()));
    }
    private static void validateRows(List<SchedulingEntry> rows) {
        if (rows.size() > 50000) throw new IllegalArgumentException("Bản lịch vượt 50000 buổi.");
        var keys = new HashSet<String>(); var positions = new HashSet<List<Object>>();
        for (var e : rows) if (e.getEntryKey() == null || !keys.add(e.getEntryKey()) || !positions.add(Arrays.asList(e.getScheduleClassId(), e.getLessonDate(), e.getStartPeriod()))) throw new IllegalArgumentException("Lặp định danh hoặc cùng lớp/ngày/tiết bắt đầu.");
    }
    private static List<Edit> edits(Input input) { if (input.edits() == null || input.edits().isEmpty() || input.edits().size() > 2000 || input.edits().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Cần 1–2000 buổi chỉnh sửa."); return input.edits(); }
    private static Set<Long> ids(List<Long> values) { if (values == null) return Set.of(); if (values.size() > 2000 || values.stream().anyMatch(v -> v == null || v <= 0) || new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("ID buổi không hợp lệ hoặc bị lặp."); return new LinkedHashSet<>(values); }
    private static Set<String> classes(List<String> values) { if (values == null) return Set.of(); if (values.size() > 100 || values.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 255) || new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Chọn tối đa 100 lớp/nhóm duy nhất."); return new LinkedHashSet<>(values); }
    private static void unlocked(SchedulingEntry e) { if (e.isLocked()) throw new IllegalArgumentException("Buổi đang khóa; mở khóa trước khi sửa hoặc bỏ."); }
    private static void date(SchedulingPlan plan, LocalDate date) { if (date == null || date.isBefore(plan.getSemesterStartDate()) || date.isAfter(plan.getSemesterStartDate().plusWeeks(100).minusDays(1))) throw new IllegalArgumentException("Ngày phải nằm trong 100 tuần của bản lịch."); }
    private static String reason(Input input) { return input.reason() == null || input.reason().isBlank() ? "Điều chỉnh " + input.action() : CatalogService.text(input.reason(), 255, "Lý do"); }
    private static SchedulingEntry copy(SchedulingEntry e) { var row = new SchedulingEntry(); org.springframework.beans.BeanUtils.copyProperties(e, row); if (row.getEntryKey() == null) row.setEntryKey("LEGACY_" + row.getId()); return row; }
    private static Session session(SchedulingEntry e, boolean withId) { return new Session(withId ? e.getId() : null, e.getEntryKey(), e.getScheduleClassId(), e.getDemand().getCourseCode(), e.getClassGroup(), e.getSessionType(), e.getLecturer().getId(), e.getLecturer().getLecturerCode(), e.getLecturerName(), e.getCourseName(), e.getScheduleParentId(), e.getStudentCount(), e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getStartPeriod(), e.getNumPeriods(), e.isLocked()); }
    private static List<Delta> deltas(List<SchedulingEntry> before, List<SchedulingEntry> after) {
        var old = before.stream().collect(Collectors.toMap(SchedulingEntry::getEntryKey, Function.identity())); var fresh = after.stream().collect(Collectors.toMap(SchedulingEntry::getEntryKey, Function.identity()));
        var keys = new TreeSet<>(old.keySet()); keys.addAll(fresh.keySet()); var result = new ArrayList<Delta>();
        for (var key : keys) if (!old.containsKey(key) || !fresh.containsKey(key) || !session(old.get(key), false).equals(session(fresh.get(key), false))) result.add(new Delta(key, old.containsKey(key) ? session(old.get(key), true) : null, fresh.containsKey(key) ? session(fresh.get(key), true) : null));
        return result;
    }
    private static List<PlanBusyDTO> difference(List<PlanBusyDTO> left, List<PlanBusyDTO> right) { var set = new HashSet<>(right); return left.stream().filter(b -> !set.contains(b)).toList(); }
    private static boolean sameSettings(Configuration a, Configuration b) {
        // Refreshing the current catalog snapshot alone is not a user change.
        return Objects.equals(a.lecturerIds(), b.lecturerIds()) && Objects.equals(a.roomIds(), b.roomIds())
                && Objects.equals(a.weights(), b.weights()) && Objects.equals(a.periodChoiceId(), b.periodChoiceId());
    }
    private String virtual(SchedulingPlan plan, Long id) { String key = "DRAFT_A" + id; int attempt = 0; while (teachers.existsById(key)) key = "DRAFT_" + hash(plan.getId() + ":" + plan.getDraftVersion().getId() + ":" + id + ":" + attempt++).substring(0, 32); return key; }
    private String uncheckedJson(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Không dựng được nội dung đề xuất.", e); } }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private SchedulingPlan plan(Long id) { return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bản lịch không tồn tại.")); }
    private static SchedulingPlanVersion draft(SchedulingPlan plan) { if (plan.getDraftVersion() == null) throw new IllegalArgumentException("Chưa có bản nháp để điều chỉnh."); return plan.getDraftVersion(); }
    private static ObjectOptimisticLockingFailureException stale(Long id) { return new ObjectOptimisticLockingFailureException("ScheduleAdjustment", id); }
}
