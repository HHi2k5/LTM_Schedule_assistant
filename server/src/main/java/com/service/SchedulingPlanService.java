package com.service;

import com.dto.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.SchedulingPlanDTO.*;
import com.exception.*;
import com.model.*;
import com.repository.*;
import com.security.AccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class SchedulingPlanService {
    private final SchedulingPlanRepository plans;
    private final SchedulingPlanVersionRepository versions;
    private final SchedulingDemandRepository demands;
    private final SchedulingEntryRepository entries;
    private final InstitutionRepository institutions;
    private final DepartmentRepository departments;
    private final InstitutionLecturerRepository lecturers;
    private final RoomRepository rooms;
    private final CatalogSchedulingService catalog;
    private final SchedulingPlanValidation validation;
    private final ScheduleService calendars;
    private final ScheduleWriteLock lock;
    private final ObjectMapper json;
    private final LecturerBusySlotRepository busySlots;
    private final TeacherRepository teachers;
    private final SchedulingChangeRepository changes;

    public List<Summary> list(String schoolCode) {
        admin();
        String code = schoolCode == null || schoolCode.isBlank() ? null : CatalogService.schoolCode(schoolCode);
        return plans.findAllByOrderByUpdatedAtDescIdDesc().stream().filter(p -> code == null || code.equals(p.getInstitution().getSchoolCode())).map(this::summary).toList();
    }

    @Transactional public Detail create(Create input) throws Exception {
        admin();
        if (input == null || input.departmentId() == null) throw new IllegalArgumentException("Chọn trường và khoa cho bản lịch.");
        ScheduleValidation.monday(input.semesterStartDate());
        var school = institutions.findById(CatalogService.schoolCode(input.schoolCode())).orElseThrow(() -> missing("Trường"));
        var department = departments.findByIdAndInstitution_SchoolCode(input.departmentId(), school.getSchoolCode()).orElseThrow(() -> missing("Khoa trong trường"));
        if (!school.isActive() || !department.isActive()) throw new IllegalArgumentException("Trường/khoa đã ngừng sử dụng.");
        var plan = new SchedulingPlan(); plan.setInstitution(school); plan.setDepartment(department);
        plan.setName(CatalogService.text(input.name(), 200, "Tên bản lịch"));
        plan.setSemesterKey(CatalogService.text(input.semester(), 80, "Học kỳ/đợt").toUpperCase(Locale.ROOT));
        plan.setSemesterStartDate(input.semesterStartDate()); plan.setCreatedAt(now()); plan.setUpdatedAt(plan.getCreatedAt());
        plans.saveAndFlush(plan); return detail(plan);
    }

    public Detail get(Long id) throws Exception { admin(); return detail(plan(id)); }
    public Options options(Long id) throws Exception {
        admin(); var plan = plan(id); return catalog.optionsForDepartment(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail generate(Long id, long expectedRevision, MultipartFile file, Configuration config) throws Exception {
        String actor = admin(); var plan = plan(id); revision(expectedRevision, plan);
        if (plan.getDraftVersion() != null && (plan.getDraftVersion().isCustomized() || plan.getDraftVersion().getVisitingChangeJson() != null))
            throw new IllegalArgumentException("Bản nháp có điều chỉnh, khóa buổi hoặc thỉnh giảng. Dùng điều chỉnh cục bộ để giữ các buổi ngoài phạm vi.");
        var sourceVersion = plan.getDraftVersion();
        var proposal = catalog.draftProposal(plan.getInstitution().getSchoolCode(), plan.getDepartment().getId(), plan.getId(), plan.getSemesterStartDate(), file, config);
        var result = proposal.result();
        long count = result.getScheduledClasses().stream().mapToLong(s -> s.getWeekSchedule().chars().filter(c -> c != '-' && c != ' ').count()).sum();
        if (count > 50000 || result.getScheduledClasses().size() > 10000) throw new IllegalArgumentException("Bản lịch vượt 50000 buổi hoặc 10000 dòng lịch; chia kế hoạch thành đợt nhỏ hơn.");
        var version = new SchedulingPlanVersion(); version.setPlan(plan); version.setVersionNumber(plan.getVersionSequence() + 1);
        version.setCreatedAt(now()); version.setCreatedBy(actor); version.setComplete(result.isComplete());
        version.setRequestJson(json.writeValueAsString(proposal.request())); version.setConfigurationJson(json.writeValueAsString(config)); version.setResultJson(json.writeValueAsString(result));
        versions.saveAndFlush(version);
        var byIndex = new HashMap<Integer, SchedulingDemand>();
        for (int index = 0; index < proposal.request().getCourses().size(); index++) {
            var course = proposal.request().getCourses().get(index); var demand = new SchedulingDemand();
            demand.setVersion(version); demand.setDemandIndex(index + 1); demand.setCourseCode(course.getCourseCode()); demand.setCohort(course.getCohort()); demand.setSnapshotJson(json.writeValueAsString(course));
            demands.save(demand); byIndex.put(index + 1, demand);
        }
        var identities = new HashMap<String, LecturerIdentity>(); result.getCatalogContext().lecturers().forEach(i -> identities.put(i.teacherId(), i));
        var pool = new HashMap<String, Room>(); rooms.findBySchoolCodeOrderByCapacityAsc(plan.getInstitution().getSchoolCode()).forEach(r -> pool.put(r.getRoomCode().toUpperCase(Locale.ROOT), r));
        var periods = ScheduleValidation.periods(proposal.request().getTargetSchool().getPeriods());
        var toSave = new ArrayList<SchedulingEntry>();
        for (var slot : result.getScheduledClasses()) {
            var identity = identities.get(slot.getTeacherId());
            var profile = lecturers.getReferenceById(identity.lecturerId());
            CatalogService.text(slot.getCourseName(), 255, "Tên buổi dạy");
            for (int week = 0; week < slot.getWeekSchedule().length(); week++) {
                char marker = slot.getWeekSchedule().charAt(week); if (marker == '-' || marker == ' ') continue;
                var entry = new SchedulingEntry(); entry.setVersion(version); entry.setDemand(byIndex.get(slot.getDemandIndex())); entry.setLecturer(profile);
                entry.setRoom(pool.get(slot.getRoom().toUpperCase(Locale.ROOT))); entry.setRoomCode(slot.getRoom());
                entry.setLessonDate(plan.getSemesterStartDate().plusDays(week * 7L + slot.getDayOfWeek() - 2));
                entry.setStartPeriod(slot.getStartPeriod()); entry.setNumPeriods(slot.getNumPeriods());
                entry.setStartTime(periods.get(slot.getStartPeriod()).getStart()); entry.setEndTime(periods.get(slot.getStartPeriod() + slot.getNumPeriods() - 1).getEnd());
                entry.setScheduleClassId(slot.getClassId()); entry.setScheduleParentId(slot.getParentClassId()); entry.setClassGroup(slot.getClassGroup());
                entry.setSessionType(slot.getSessionType()); entry.setStudentCount(slot.getStudentCount()); entry.setCourseName(slot.getCourseName()); entry.setLecturerName(slot.getTeacherName()); toSave.add(entry);
            }
        }
        entries.saveAll(toSave);
        plan.setVersionSequence(version.getVersionNumber()); plan.setDraftVersion(version); plan.setUpdatedAt(now()); plans.saveAndFlush(plan);
        recordChange(plan, sourceVersion, version, "GENERATE", toSave.size(), "Xếp lịch từ danh mục", "{}");
        return detail(plan);
    }

    public List<Conflict> validate(Long id, Long expectedRevision) throws Exception {
        admin(); var plan = plan(id); revision(expectedRevision, plan); var version = draft(plan);
        return validation.check(plan, version, request(version), result(version, false), entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(version.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail confirm(Long id, Long expectedRevision) throws Exception {
        String actor = admin(); lock.acquire(); var plan = plan(id); revision(expectedRevision, plan); var version = draft(plan);
        if (plan.getConfirmedVersion() != null && version.getId().equals(plan.getConfirmedVersion().getId())) return detail(plan);
        var input = request(version); var result = result(version, false);
        var rows = entries.findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(version.getId());
        var conflicts = validation.check(plan, version, input, result, rows);
        if (!conflicts.isEmpty()) throw new SchedulingPlanConflictException(conflicts);
        var used = new LinkedHashSet<Long>(); rows.forEach(e -> used.add(e.getLecturer().getId()));
        var busy = busySlots.findByVersion_Id(version.getId()); busy.forEach(b -> used.add(b.getLecturer().getId()));
        var identities = catalog.identities(plan.getInstitution().getSchoolCode(), used, plan.getSemesterStartDate());
        var lookup = new HashMap<Long, String>(); identities.forEach(i -> lookup.put(i.lecturerId(), i.teacherId()));
        busy.forEach(b -> b.setScheduleTeacher(teachers.getReferenceById(lookup.get(b.getLecturer().getId()))));
        calendars.replacePlanProjection(plan.getId(), input, rows, lookup);
        var old = new HashMap<String, String>(); result.getCatalogContext().lecturers().forEach(i -> { if (lookup.containsKey(i.lecturerId())) old.put(i.teacherId(), lookup.get(i.lecturerId())); });
        result.getScheduledClasses().forEach(s -> s.setTeacherId(old.getOrDefault(s.getTeacherId(), s.getTeacherId())));
        result.getTeacherAllocations().forEach(a -> a.setTeacherId(old.getOrDefault(a.getTeacherId(), a.getTeacherId())));
        result.getClassWorkloads().forEach(w -> w.setTeacherId(old.getOrDefault(w.getTeacherId(), w.getTeacherId())));
        var publishedIdentities = result.getCatalogContext().lecturers().stream().map(i -> identities.stream().filter(actual -> actual.lecturerId().equals(i.lecturerId()))
                .map(actual -> new LecturerIdentity(actual.lecturerId(), i.lecturerCode(), actual.teacherId(), actual.accountTeacherId(), i.lecturerType())).findFirst().orElse(i)).toList();
        result.setCatalogContext(new RunContext(plan.getInstitution().getSchoolCode(), result.getCatalogContext().sourceSnapshot(), result.getCatalogContext().roomIds(), publishedIdentities, result.getCatalogContext().periodChoiceId()));
        result.setSaved(true); result.setStatus("SUCCESS"); result.setMessage("Đã xác nhận và cập nhật lịch của khoa trong bản lịch này.");
        version.setPublicationJson(json.writeValueAsString(result)); version.setConfirmedAt(now()); version.setConfirmedBy(actor); versions.save(version);
        plan.setConfirmedVersion(version); plan.setUpdatedAt(now()); plans.saveAndFlush(plan);
        recordChange(plan, version, version, "CONFIRM", rows.size(), "Xác nhận bản nháp", "{}"); return detail(plan);
    }

    public Version version(Long id, Long versionId) throws Exception {
        admin(); var plan = plan(id); var version = versions.findByIdAndPlan_Id(versionId, id).orElseThrow(() -> missing("Phiên bản của bản lịch"));
        return version(plan, version);
    }

    public EntryPage entryPage(Long id, Long versionId, int page) {
        admin(); if (page < 0) throw new IllegalArgumentException("Trang không hợp lệ.");
        versions.findByIdAndPlan_Id(versionId, id).orElseThrow(() -> missing("Phiên bản của bản lịch"));
        var data = entries.findByVersion_Id(versionId, PageRequest.of(page, 25, Sort.by("lessonDate", "startTime", "id")));
        return new EntryPage(data.getContent().stream().map(e -> new Entry(e.getId(), e.getLecturer().getId(), e.getLecturer().getLecturerCode(), e.getLecturerName(), e.getDemand().getCourseCode(), e.getCourseName(), e.getDemand().getCohort(),
                e.getClassGroup(), e.getSessionType(), e.getStudentCount(), e.getRoomCode(), e.getLessonDate(), e.getStartTime(), e.getEndTime(), e.getStartPeriod(), e.getNumPeriods(), e.getEntryKey(), e.isLocked())).toList(), page, data.getTotalPages(), data.getTotalElements());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    Detail storeVisitingDraft(SchedulingPlan plan, SchoolWideScheduleRequest request, Configuration config, AutoScheduleResponse result,
                             List<SchedulingEntry> canonical, VisitingLecturerDTO.ChangeInfo change) throws Exception {
        return storeAdjustedDraft(plan, request, config, result, canonical, change, null, true, canonical.size());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    Detail storeAdjustedDraft(SchedulingPlan plan, SchoolWideScheduleRequest request, Configuration config, AutoScheduleResponse result,
                             List<SchedulingEntry> canonical, VisitingLecturerDTO.ChangeInfo visitingChange, ScheduleAdjustmentDTO.ChangeInfo adjustment,
                             boolean customized, int affected) throws Exception {
        if (canonical.size() > 50000 || result.getScheduledClasses().size() > 10000 || PublishedBusyReservations.own(request).size() > 10000)
            throw new IllegalArgumentException("Bản lịch vượt giới hạn buổi học hoặc lịch bận; chia phạm vi nhỏ hơn.");
        var version = new SchedulingPlanVersion(); version.setPlan(plan); version.setVersionNumber(plan.getVersionSequence() + 1);
        var sourceVersion = plan.getDraftVersion(); version.setCustomized(customized);
        version.setCreatedAt(now()); version.setCreatedBy(admin()); version.setComplete(result.isComplete());
        version.setRequestJson(json.writeValueAsString(request)); version.setConfigurationJson(json.writeValueAsString(config)); version.setResultJson(json.writeValueAsString(result));
        version.setVisitingChangeJson(visitingChange == null ? null : json.writeValueAsString(visitingChange));
        version.setAdjustmentJson(adjustment == null ? null : json.writeValueAsString(adjustment)); versions.saveAndFlush(version);
        var demandMap = new HashMap<Integer, SchedulingDemand>();
        for (int i = 0; i < request.getCourses().size(); i++) {
            var c = request.getCourses().get(i); var demand = new SchedulingDemand(); demand.setVersion(version); demand.setDemandIndex(i + 1);
            demand.setCourseCode(c.getCourseCode()); demand.setCohort(c.getCohort()); demand.setSnapshotJson(json.writeValueAsString(c)); demands.save(demand); demandMap.put(i + 1, demand);
        }
        for (var source : canonical) {
            var e = new SchedulingEntry(); org.springframework.beans.BeanUtils.copyProperties(source, e, "id", "version", "demand");
            e.setVersion(version); e.setDemand(demandMap.get(source.getDemand().getDemandIndex())); entries.save(e);
        }
        for (var source : PublishedBusyReservations.own(request)) {
            var b = new LecturerBusySlot(); b.setVersion(version); b.setLecturer(lecturers.getReferenceById(source.lecturerId()));
            b.setBusyDate(source.date()); b.setStartTime(source.startTime()); b.setEndTime(source.endTime()); b.setReason(source.reason()); busySlots.save(b);
        }
        plan.setVersionSequence(version.getVersionNumber()); plan.setDraftVersion(version); plan.setUpdatedAt(now()); plans.saveAndFlush(plan);
        recordChange(plan, sourceVersion, version, adjustment == null ? "VISITING" : adjustment.action(), affected,
                adjustment == null ? "Cập nhật thỉnh giảng" : adjustment.reason(), json.writeValueAsString(adjustment == null ? visitingChange : adjustment));
        return detail(plan);
    }

    public ScheduleAdjustmentDTO.LogPage changeLog(Long id, int page) {
        admin(); plan(id); if (page < 0) throw new IllegalArgumentException("Trang nhật ký không hợp lệ.");
        var rows = changes.findByPlan_IdOrderByIdDesc(id, PageRequest.of(page, 20));
        return new ScheduleAdjustmentDTO.LogPage(rows.getContent().stream().map(c -> new ScheduleAdjustmentDTO.Log(c.getId(), c.getAction(),
                c.getSourceVersion() == null ? null : c.getSourceVersion().getId(), c.getSourceVersion() == null ? null : c.getSourceVersion().getVersionNumber(),
                c.getResultVersion().getId(), c.getResultVersion().getVersionNumber(), c.getActor(), c.getCreatedAt().atOffset(ZoneOffset.UTC), c.getAffectedEntries(), c.getReason())).toList(), page, rows.getTotalPages(), rows.getTotalElements());
    }
    private void recordChange(SchedulingPlan plan, SchedulingPlanVersion source, SchedulingPlanVersion result, String action, int affected, String reason, String scope) {
        var change = new SchedulingChange(); change.setPlan(plan); change.setSourceVersion(source); change.setResultVersion(result); change.setAction(action); change.setActor(admin());
        change.setCreatedAt(now()); change.setAffectedEntries(affected); change.setReason(reason); change.setScopeJson(scope); changes.saveAndFlush(change);
    }

    Detail detail(SchedulingPlan plan) throws Exception {
        var history = versions.findByPlan_IdOrderByVersionNumberDesc(plan.getId()).stream().map(v -> versionSummary(plan, v)).toList();
        return new Detail(summary(plan), plan.getDraftVersion() == null ? null : version(plan, plan.getDraftVersion()), history);
    }
    private Version version(SchedulingPlan plan, SchedulingPlanVersion version) throws Exception {
        return new Version(versionSummary(plan, version), json.readValue(version.getConfigurationJson(), Configuration.class), request(version).getCourses(), result(version, true), entries.countByVersion_Id(version.getId()),
                version.getVisitingChangeJson() == null ? null : json.readValue(version.getVisitingChangeJson(), VisitingLecturerDTO.ChangeInfo.class), PublishedBusyReservations.own(request(version)),
                version.getAdjustmentJson() == null ? null : json.readValue(version.getAdjustmentJson(), ScheduleAdjustmentDTO.ChangeInfo.class), version.isCustomized());
    }
    private Summary summary(SchedulingPlan p) {
        boolean confirmed = p.getConfirmedVersion() != null && p.getDraftVersion() != null && p.getConfirmedVersion().getId().equals(p.getDraftVersion().getId());
        return new Summary(p.getId(), p.getInstitution().getSchoolCode(), p.getInstitution().getName(), p.getDepartment().getId(), p.getDepartment().getName(), p.getName(), p.getSemesterKey(), p.getSemesterStartDate(), p.getRevision(), confirmed ? "CONFIRMED" : "DRAFT",
                p.getDraftVersion() == null ? null : p.getDraftVersion().getVersionNumber(), p.getConfirmedVersion() == null ? null : p.getConfirmedVersion().getVersionNumber(), p.getUpdatedAt());
    }
    private VersionSummary versionSummary(SchedulingPlan p, SchedulingPlanVersion v) {
        return new VersionSummary(v.getId(), v.getVersionNumber(), v.isComplete(), v.getCreatedAt(), v.getCreatedBy(), v.getConfirmedAt(), p.getDraftVersion() != null && p.getDraftVersion().getId().equals(v.getId()), p.getConfirmedVersion() != null && p.getConfirmedVersion().getId().equals(v.getId()));
    }
    private SchedulingPlan plan(Long id) { return plans.findById(id).orElseThrow(() -> missing("Bản lịch")); }
    private static SchedulingPlanVersion draft(SchedulingPlan p) { if (p.getDraftVersion() == null) throw new IllegalArgumentException("Chưa có bản nháp đã lưu."); return p.getDraftVersion(); }
    private SchoolWideScheduleRequest request(SchedulingPlanVersion v) throws Exception { return json.readValue(v.getRequestJson(), SchoolWideScheduleRequest.class); }
    private AutoScheduleResponse result(SchedulingPlanVersion v, boolean published) throws Exception { return json.readValue(published && v.getPublicationJson() != null ? v.getPublicationJson() : v.getResultJson(), AutoScheduleResponse.class); }
    private static void revision(Long expected, SchedulingPlan p) { if (expected == null) throw new IllegalArgumentException("Cần revision của bản lịch."); if (expected != p.getRevision()) throw new ObjectOptimisticLockingFailureException(SchedulingPlan.class, p.getId()); }
    private static ResourceNotFoundException missing(String name) { return new ResourceNotFoundException(name + " không tồn tại."); }
    private static LocalDateTime now() { return LocalDateTime.now(Clock.systemUTC()); }
    static String admin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal account) || !"ADMIN".equals(account.role())) throw new AccessDeniedException("Chỉ ADMIN được quản lý bản lịch.");
        return account.teacherId();
    }
}
