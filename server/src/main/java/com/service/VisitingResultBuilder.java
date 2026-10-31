package com.service;

import com.dto.*;
import com.dto.CatalogSchedulingDTO.*;
import com.model.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Rebuilds exports and workload from canonical occurrences, never by adding hours to an old result. */
final class VisitingResultBuilder {
    private VisitingResultBuilder() {}
    static AutoScheduleResponse rebuild(SchoolWideScheduleRequest request, AutoScheduleResponse source, List<SchedulingEntry> entries,
                                         List<ClassWorkloadDTO> workloads, RunContext context) {
        var identities = new HashMap<Long, LecturerIdentity>(); context.lecturers().forEach(i -> identities.put(i.lecturerId(), i));
        var perClass = new HashMap<String, List<SchedulingEntry>>(); entries.forEach(e -> perClass.computeIfAbsent(e.getScheduleClassId(), ignored -> new ArrayList<>()).add(e));
        var warnings = new ArrayList<String>(); var allocations = new LinkedHashMap<List<Object>, TeacherAllocationDTO>();
        for (var old : source.getTeacherAllocations() == null ? List.<TeacherAllocationDTO>of() : source.getTeacherAllocations()) {
            var a = TeacherAllocationDTO.builder().courseCode(old.getCourseCode()).cohort(old.getCohort()).teacherId(old.getTeacherId()).teacherName(old.getTeacherName()).sessionType(old.getSessionType()).weight(old.getWeight()).build();
            allocations.put(key(a.getCourseCode(), a.getCohort(), a.getTeacherId(), a.getSessionType()), a);
        }
        for (var w : workloads) {
            int sum = perClass.getOrDefault(w.getClassId(), List.of()).stream().mapToInt(SchedulingEntry::getNumPeriods).sum();
            if (sum > w.getRequiredPeriods()) throw new IllegalArgumentException(w.getCourseCode() + " / " + w.getClassGroup() + ": lịch cố định vượt tổng tiết cần dạy.");
            w.setScheduledPeriods(sum); w.setMissingPeriods(w.getRequiredPeriods() - sum);
            if (sum < w.getRequiredPeriods()) warnings.add(w.getCourseCode() + " / " + w.getClassGroup() + ": còn thiếu " + w.getMissingPeriods() + " tiết.");
            if (w.getTeacherId() == null) continue;
            var course = request.getCourses().get(w.getDemandIndex() - 1);
            var candidate = course.getTeachers().stream().filter(t -> w.getTeacherId().equals(t.getTeacherId())).findFirst().orElse(null);
            var a = allocations.computeIfAbsent(key(w.getCourseCode(), w.getCohort(), w.getTeacherId(), w.getSessionType()), ignored -> TeacherAllocationDTO.builder()
                    .courseCode(w.getCourseCode()).cohort(w.getCohort()).teacherId(w.getTeacherId()).sessionType(w.getSessionType()).teacherName(candidate == null ? w.getTeacherId() : candidate.getTeacherName()).build());
            if (candidate != null) { a.setTeacherName(candidate.getTeacherName()); a.setWeight("PRACTICE".equals(w.getSessionType()) ? candidate.getPracticeWeight() : candidate.getTheoryWeight()); }
            a.setTargetClasses(a.getTargetClasses() + 1); if (sum == w.getRequiredPeriods()) a.setAssignedClasses(a.getAssignedClasses() + 1); a.setScheduledPeriods(a.getScheduledPeriods() + sum);
        }
        var merged = new LinkedHashMap<List<Object>, GeneratedClassSlot>();
        for (var e : entries.stream().sorted(Comparator.comparing(SchedulingEntry::getLessonDate).thenComparing(SchedulingEntry::getStartTime).thenComparing(SchedulingEntry::getScheduleClassId)).toList()) {
            var identity = identities.get(e.getLecturer().getId()); var course = request.getCourses().get(e.getDemand().getDemandIndex() - 1);
            var key = key(e.getScheduleClassId(), identity.teacherId(), e.getRoomCode(), e.getLessonDate().getDayOfWeek().name(), e.getStartPeriod(), e.getNumPeriods());
            var slot = merged.computeIfAbsent(key, ignored -> GeneratedClassSlot.builder().demandIndex(e.getDemand().getDemandIndex()).classId(e.getScheduleClassId()).parentClassId(e.getScheduleParentId())
                    .studyStartDate(course.getStudyStartDate()).studyEndDate(course.getStudyEndDate()).baseCourseName(course.getCourseName()).credits(course.getCredits())
                    .teacherId(identity.teacherId()).teacherName(e.getLecturerName()).courseCode(course.getCourseCode()).courseName(e.getCourseName()).cohort(course.getCohort())
                    .classGroup(e.getClassGroup()).subGroup("PRACTICE".equals(e.getSessionType()) ? e.getClassGroup() : "Cả lớp").sessionType(e.getSessionType()).studentCount(e.getStudentCount())
                    .room(e.getRoomCode()).roomType(e.getRoom().getRoomType()).roomCapacity(e.getRoom().getCapacity()).dayOfWeek(e.getLessonDate().getDayOfWeek().getValue() + 1)
                    .startPeriod(e.getStartPeriod()).numPeriods(e.getNumPeriods()).weekSchedule("").phaseNote("Bản nháp có phân công thỉnh giảng").build());
            int week = (int) ChronoUnit.DAYS.between(request.getSemesterStartDate(), e.getLessonDate()) / 7;
            var weeks = new StringBuilder(slot.getWeekSchedule()); while (weeks.length() <= week) weeks.append('-'); weeks.setCharAt(week, '1'); slot.setWeekSchedule(weeks.toString());
        }
        return AutoScheduleResponse.builder().catalogContext(context).teacherAllocations(new ArrayList<>(allocations.values())).classWorkloads(workloads)
                .totalLessonsScheduled(entries.size()).saved(false).complete(warnings.isEmpty() && !entries.isEmpty()).schoolId(source.getSchoolId()).schoolName(source.getSchoolName())
                .semesterStartDate(request.getSemesterStartDate()).periods(request.getTargetSchool().getPeriods()).status("NOT_SAVED").message("Đề xuất thỉnh giảng chưa cập nhật lịch cá nhân.")
                .totalRoomsAvailable(context.roomIds().size()).totalClassesScheduled((int) entries.stream().map(SchedulingEntry::getScheduleClassId).distinct().count())
                .scheduledClasses(new ArrayList<>(merged.values())).unscheduledWarnings(warnings).build();
    }
    private static List<Object> key(Object... values) { return Arrays.asList(values); }
}
