package com.service;

import com.dto.*;
import com.model.*;
import com.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AutoScheduleService {

    private final TeacherRepository teacherRepository;
    private final ScheduleService scheduleService;

    // 4 ca chuẩn trong 1 ngày (Sáng 5 tiết: [1-3, 4-5], Chiều 5 tiết: [6-8, 9-10])
    private static final int[][] DAILY_SLOTS = {
            {1, 3}, // Slot 0 - Ca 1 Sáng: Tiết 1-3 (3 tiết)
            {4, 2}, // Slot 1 - Ca 2 Sáng: Tiết 4-5 (2 tiết)
            {6, 3}, // Slot 2 - Ca 1 Chiều: Tiết 6-8 (3 tiết)
            {9, 2}  // Slot 3 - Ca 2 Chiều: Tiết 9-10 (2 tiết)
    };

    /**
     * Tự động khởi tạo 35 phòng học: 7 tầng (1 -> 7), mỗi tầng 5 phòng (01 -> 05)
     */
    private List<String> generate35Rooms(String buildingSuffix) {
        List<String> rooms = new ArrayList<>(35);
        String suffix = (buildingSuffix != null && !buildingSuffix.isBlank()) ? "-" + buildingSuffix.trim() : "";
        for (int floor = 1; floor <= 7; floor++) {
            for (int r = 1; r <= 5; r++) {
                rooms.add(String.format("%d%02d%s", floor, r, suffix)); // VD: 101-A2 ... 705-A2
            }
        }
        return rooms;
    }

    @Transactional
    public AutoScheduleResponse generateSchoolWideSchedule(SchoolWideScheduleRequest request) {
        int totalWeeks = request.getTotalWeeks() > 0 ? request.getTotalWeeks() : 16;
        int halfWeeks = totalWeeks / 2;

        String firstHalfWeeks = buildWeekString(1, halfWeeks, totalWeeks);
        String secondHalfWeeks = buildWeekString(halfWeeks + 1, totalWeeks, totalWeeks);

        // 1. Tạo danh sách 35 phòng học từ tầng 1 đến tầng 7
        List<String> rooms = generate35Rooms(request.getBuildingSuffix());

        // Quản lý phòng đã bị chiếm dụng trên toàn trường: Key = "day_slotIdx" -> Set<Room>
        Map<String, Set<String>> occupiedRooms = new HashMap<>();

        // Quản lý các ca giảng viên đã bị xếp lịch nội bộ (để 1 GV dạy 2 môn không tự đụng nhau)
        // Key = teacherId -> Set<"day_slotIdx">
        Map<String, Set<String>> internalTeacherBusySlots = new HashMap<>();

        // Lưu danh sách môn học theo từng giảng viên để ghi xuống MySQL
        Map<String, List<TeachingCourseDTO>> coursesByTeacher = new HashMap<>();
        Map<String, String> teacherNames = new HashMap<>();

        List<GeneratedClassSlot> allGeneratedSlots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalPhysicalClassesScheduled = 0;

        // 2. Duyệt qua từng môn học trong toàn trường
        for (CourseDemandDTO course : request.getCourses()) {
            List<TeacherCandidateDTO> teachers = course.getTeachers();
            if (teachers == null || teachers.isEmpty()) {
                warnings.add("Môn " + course.getCourseName() + " không có giảng viên nào được phân công!");
                continue;
            }

            // Tự động chia đều số lớp của môn học cho các giảng viên (nếu chưa chỉ định)
            Map<String, Integer> teacherClassQuota = allocateClassesToTeachers(course.getTotalClassesToOpen(), teachers);
            int globalGroupCounter = 1; // Đánh số Nhóm 01, 02... tăng dần cho toàn bộ môn học

            for (TeacherCandidateDTO teacher : teachers) {
                String teacherId = teacher.getTeacherId();
                int classesToTeach = teacherClassQuota.getOrDefault(teacherId, 0);
                if (classesToTeach <= 0) continue;

                teacherNames.put(teacherId, teacher.getTeacherName());
                coursesByTeacher.putIfAbsent(teacherId, new ArrayList<>());
                internalTeacherBusySlots.putIfAbsent(teacherId, new HashSet<>());

                // Lấy lịch bận ở các trường khác (giảng viên thỉnh giảng) từ MySQL
                List<BusyTimeSlot> externalBusySlots = getExternalBusySlots(
                        teacherId, request.getTargetSchool().getSchoolId()
                );

                // Ràng buộc: 1 môn dạy trong 1 đến 2 ngày (<=4 lớp: 1 ngày, 5-8 lớp: 2 ngày)
                int maxDaysForThisCourse = (classesToTeach <= 4) ? 1 : 2;
                int scheduledForTeacher = 0;
                int daysUsed = 0;

                // Sắp xếp thứ tự ưu tiên ngày (từ Thứ 2 -> Thứ 7), ưu tiên ngày giảng viên chưa phải lên trường
                List<Integer> daysOrder = Arrays.asList(2, 3, 4, 5, 6, 7);

                for (int day : daysOrder) {
                    if (scheduledForTeacher >= classesToTeach || daysUsed >= maxDaysForThisCourse) break;

                    // Tìm các slot trống trong ngày `day` (không trùng trường khác & không trùng môn khác nội bộ)
                    List<Integer> freeSlots = new ArrayList<>();
                    for (int slotIdx = 0; slotIdx < DAILY_SLOTS.length; slotIdx++) {
                        int startP = DAILY_SLOTS[slotIdx][0];
                        int numP = DAILY_SLOTS[slotIdx][1];
                        String internalKey = day + "_" + slotIdx;

                        boolean isInternallyBusy = internalTeacherBusySlots.get(teacherId).contains(internalKey);
                        boolean isExternallyBusy = isTeacherBusyExternally(startP, numP, day, request.getTargetSchool(), externalBusySlots);

                        if (!isInternallyBusy && !isExternallyBusy) {
                            freeSlots.add(slotIdx);
                        }
                    }

                    if (freeSlots.isEmpty()) continue;

                    boolean usedThisDay = false;
                    int classesInThisDay = 0; // Tối đa 4 lớp / 1 ngày / 1 môn

                    for (int i = 0; i < freeSlots.size() && scheduledForTeacher < classesToTeach && classesInThisDay < 4; i++) {
                        int slotIdx = freeSlots.get(i);
                        int startP1 = DAILY_SLOTS[slotIdx][0];
                        int numP1 = DAILY_SLOTS[slotIdx][1];

                        // Kiểm tra xem có thể ghép cặp 2 lớp cùng buổi (Sáng: slot 0-1, Chiều: slot 2-3) để ĐẢO TIẾT giữa kỳ không
                        boolean canPairInSameSession = (slotIdx == 0 || slotIdx == 2)
                                && freeSlots.contains(slotIdx + 1)
                                && (scheduledForTeacher + 1 < classesToTeach)
                                && (classesInThisDay + 1 < 4);

                        if (canPairInSameSession) {
                            int nextSlotIdx = slotIdx + 1;
                            int startP2 = DAILY_SLOTS[nextSlotIdx][0];
                            int numP2 = DAILY_SLOTS[nextSlotIdx][1];

                            // Cần 2 phòng học trống xuyên suốt buổi đó cho 2 lớp để không đụng phòng khi đảo ca
                            List<String> twoRooms = findAvailableRoomsForSession(day, slotIdx, nextSlotIdx, rooms, occupiedRooms, 2);

                            if (twoRooms.size() == 2) {
                                String roomA = twoRooms.get(0);
                                String roomB = twoRooms.get(1);

                                String group1 = String.format("%02d", globalGroupCounter++);
                                String group2 = String.format("%02d", globalGroupCounter++);

                                // LỚP 1 (Phòng A): Nửa đầu học 3 tiết (Ca 1), Nửa sau đảo xuống 2 tiết (Ca 2)
                                recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                        teacher, course, group1, roomA, day, startP1, numP1, firstHalfWeeks,
                                        "Nửa kỳ đầu: 3 tiết (Tiết " + startP1 + "-" + (startP1 + numP1 - 1) + ")");
                                recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                        teacher, course, group1, roomA, day, startP2, numP2, secondHalfWeeks,
                                        "Nửa kỳ sau đảo: 2 tiết (Tiết " + startP2 + "-" + (startP2 + numP2 - 1) + ")");

                                // LỚP 2 (Phòng B): Nửa đầu học 2 tiết (Ca 2), Nửa sau đảo lên 3 tiết (Ca 1)
                                recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                        teacher, course, group2, roomB, day, startP2, numP2, firstHalfWeeks,
                                        "Nửa kỳ đầu: 2 tiết (Tiết " + startP2 + "-" + (startP2 + numP2 - 1) + ")");
                                recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                        teacher, course, group2, roomB, day, startP1, numP1, secondHalfWeeks,
                                        "Nửa kỳ sau đảo: 3 tiết (Tiết " + startP1 + "-" + (startP1 + numP1 - 1) + ")");

                                // Đánh dấu phòng bận và giảng viên bận ở cả 2 ca
                                markRoomOccupied(day, slotIdx, roomA, occupiedRooms);
                                markRoomOccupied(day, nextSlotIdx, roomA, occupiedRooms);
                                markRoomOccupied(day, slotIdx, roomB, occupiedRooms);
                                markRoomOccupied(day, nextSlotIdx, roomB, occupiedRooms);

                                internalTeacherBusySlots.get(teacherId).add(day + "_" + slotIdx);
                                internalTeacherBusySlots.get(teacherId).add(day + "_" + nextSlotIdx);

                                scheduledForTeacher += 2;
                                classesInThisDay += 2;
                                totalPhysicalClassesScheduled += 2;
                                usedThisDay = true;
                                i++; // Bỏ qua slot kế tiếp vì đã ghép cặp
                                continue;
                            }
                        }

                        // Trường hợp lẻ 1 lớp cuối cùng: Nửa đầu học 3 tiết, nửa sau giảm xuống 2 tiết tại cùng phòng
                        String singleRoom = findSingleAvailableRoom(day, slotIdx, rooms, occupiedRooms);
                        if (singleRoom != null) {
                            String group = String.format("%02d", globalGroupCounter++);

                            recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                    teacher, course, group, singleRoom, day, startP1, 3, firstHalfWeeks,
                                    "Nửa kỳ đầu: 3 tiết");
                            recordClassSlot(allGeneratedSlots, coursesByTeacher.get(teacherId),
                                    teacher, course, group, singleRoom, day, startP1, 2, secondHalfWeeks,
                                    "Nửa kỳ sau: 2 tiết");

                            markRoomOccupied(day, slotIdx, singleRoom, occupiedRooms);
                            internalTeacherBusySlots.get(teacherId).add(day + "_" + slotIdx);

                            scheduledForTeacher++;
                            classesInThisDay++;
                            totalPhysicalClassesScheduled++;
                            usedThisDay = true;
                        }
                    }

                    if (usedThisDay) {
                        daysUsed++;
                    }
                }

                if (scheduledForTeacher < classesToTeach) {
                    warnings.add(String.format("GV %s (%s) môn %s: Chỉ xếp được %d/%d lớp (do kín phòng hoặc vướng lịch trường khác).",
                            teacher.getTeacherName(), teacherId, course.getCourseName(), scheduledForTeacher, classesToTeach));
                }
            }
        }

        // 3. Lưu toàn bộ kết quả xếp lịch của tất cả giảng viên vào MySQL
        for (Map.Entry<String, List<TeachingCourseDTO>> entry : coursesByTeacher.entrySet()) {
            String teacherId = entry.getKey();
            SchoolUploadRequest saveReq = SchoolUploadRequest.builder()
                    .teacherId(teacherId)
                    .teacherName(teacherNames.get(teacherId))
                    .semesterStartDate(request.getSemesterStartDate())
                    .school(request.getTargetSchool())
                    .courses(entry.getValue())
                    .build();
            scheduleService.saveOrUpdateSchoolSchedule(saveReq);
        }

        return AutoScheduleResponse.builder()
                .status(warnings.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS")
                .message("Đã hoàn tất xếp lịch toàn trường trên hệ thống 35 phòng học (Tầng 1 - Tầng 7)!")
                .totalRoomsAvailable(rooms.size())
                .totalClassesScheduled(totalPhysicalClassesScheduled)
                .scheduledClasses(allGeneratedSlots)
                .unscheduledWarnings(warnings)
                .build();
    }

    /**
     * Chia đều số lớp của môn học cho danh sách giảng viên
     */
    private Map<String, Integer> allocateClassesToTeachers(int totalClasses, List<TeacherCandidateDTO> teachers) {
        Map<String, Integer> allocation = new LinkedHashMap<>();
        int remaining = totalClasses;
        List<TeacherCandidateDTO> autoAssignTeachers = new ArrayList<>();

        for (TeacherCandidateDTO t : teachers) {
            if (t.getAssignedClasses() != null && t.getAssignedClasses() > 0) {
                allocation.put(t.getTeacherId(), t.getAssignedClasses());
                remaining -= t.getAssignedClasses();
            } else {
                autoAssignTeachers.add(t);
            }
        }

        if (!autoAssignTeachers.isEmpty() && remaining > 0) {
            int base = remaining / autoAssignTeachers.size();
            int mod = remaining % autoAssignTeachers.size();
            for (int i = 0; i < autoAssignTeachers.size(); i++) {
                int count = base + (i < mod ? 1 : 0);
                allocation.put(autoAssignTeachers.get(i).getTeacherId(), count);
            }
        }
        return allocation;
    }

    private void recordClassSlot(
            List<GeneratedClassSlot> outputList,
            List<TeachingCourseDTO> dbDtoList,
            TeacherCandidateDTO teacher,
            CourseDemandDTO course,
            String groupName,
            String room,
            int day,
            int startPeriod,
            int numPeriods,
            String weekSchedule,
            String phaseNote) {

        String displayCourseName = course.getCourseName() + " - Nhóm " + groupName;

        outputList.add(GeneratedClassSlot.builder()
                .teacherId(teacher.getTeacherId())
                .teacherName(teacher.getTeacherName())
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .classGroup(groupName)
                .room(room)
                .dayOfWeek(day)
                .startPeriod(startPeriod)
                .numPeriods(numPeriods)
                .weekSchedule(weekSchedule)
                .phaseNote(phaseNote)
                .build());

        dbDtoList.add(TeachingCourseDTO.builder()
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .room(room)
                .dayOfWeek(day)
                .startPeriod(startPeriod)
                .numPeriods(numPeriods)
                .weekSchedule(weekSchedule)
                .build());
    }

    private List<BusyTimeSlot> getExternalBusySlots(String teacherId, String targetSchoolId) {
        List<BusyTimeSlot> busySlots = new ArrayList<>();
        Optional<Teacher> teacherOpt = teacherRepository.findTeacherWithFullSchedule(teacherId);
        if (teacherOpt.isEmpty()) return busySlots;

        for (School school : teacherOpt.get().getSchools()) {
            if (school.getSchoolCode().equalsIgnoreCase(targetSchoolId)) continue;

            for (TeachingCourse c : school.getCourses()) {
                int endP = c.getStartPeriod() + c.getNumPeriods() - 1;
                LocalTime start = school.getPeriods().stream()
                        .filter(p -> p.getPeriodNumber() == c.getStartPeriod())
                        .map(SchoolPeriod::getStartTime)
                        .findFirst().orElse(LocalTime.of(7, 0));
                LocalTime end = school.getPeriods().stream()
                        .filter(p -> p.getPeriodNumber() == endP)
                        .map(SchoolPeriod::getEndTime)
                        .findFirst().orElse(LocalTime.of(11, 0));

                busySlots.add(new BusyTimeSlot(c.getDayOfWeek(), start, end));
            }
        }
        return busySlots;
    }

    private boolean isTeacherBusyExternally(int startPeriod, int numPeriods, int dayOfWeek,
                                            SchoolConfigDTO targetSchool, List<BusyTimeSlot> externalBusySlots) {
        int endPeriod = startPeriod + numPeriods - 1;
        LocalTime candidateStart = targetSchool.getPeriods().stream()
                .filter(p -> p.getPeriod() == startPeriod)
                .map(PeriodTimeDTO::getStart)
                .findFirst().orElse(LocalTime.of(7, 0));

        LocalTime candidateEnd = targetSchool.getPeriods().stream()
                .filter(p -> p.getPeriod() == endPeriod)
                .map(PeriodTimeDTO::getEnd)
                .findFirst().orElse(LocalTime.of(11, 50));

        for (BusyTimeSlot busy : externalBusySlots) {
            if (busy.dayOfWeek == dayOfWeek) {
                LocalTime maxStart = candidateStart.isAfter(busy.start) ? candidateStart : busy.start;
                LocalTime minEnd = candidateEnd.isBefore(busy.end) ? candidateEnd : busy.end;
                if (maxStart.isBefore(minEnd)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> findAvailableRoomsForSession(int day, int slot1, int slot2,
                                                      List<String> allRooms, Map<String, Set<String>> occupied, int countNeeded) {
        Set<String> used1 = occupied.getOrDefault(day + "_" + slot1, Collections.emptySet());
        Set<String> used2 = occupied.getOrDefault(day + "_" + slot2, Collections.emptySet());
        List<String> result = new ArrayList<>();
        for (String r : allRooms) {
            if (!used1.contains(r) && !used2.contains(r)) {
                result.add(r);
                if (result.size() == countNeeded) break;
            }
        }
        return result;
    }

    private String findSingleAvailableRoom(int day, int slotIdx, List<String> allRooms, Map<String, Set<String>> occupied) {
        Set<String> used = occupied.getOrDefault(day + "_" + slotIdx, Collections.emptySet());
        for (String r : allRooms) {
            if (!used.contains(r)) return r;
        }
        return null;
    }

    private void markRoomOccupied(int day, int slotIdx, String room, Map<String, Set<String>> occupied) {
        occupied.computeIfAbsent(day + "_" + slotIdx, k -> new HashSet<>()).add(room);
    }

    private String buildWeekString(int fromWeek, int toWeek, int totalWeeks) {
        StringBuilder sb = new StringBuilder();
        for (int w = 1; w <= totalWeeks; w++) {
            if (w >= fromWeek && w <= toWeek) {
                sb.append(w % 10);
            } else {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private record BusyTimeSlot(int dayOfWeek, LocalTime start, LocalTime end) {}
}