package com.service;

import com.dto.*;
import com.model.*;
import com.repository.RoomRepository;
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
    private final RoomRepository roomRepository;
    private final ScheduleService scheduleService;

    // Các tiết bắt đầu ca chuẩn trong ngày (Tiết 1, Tiết 4, Tiết 7, Tiết 10)
    private static final int[] DEFAULT_START_PERIODS = {1, 4, 7, 10};

    // Sĩ số tối đa của 1 Tổ thực hành phòng LAB
    private static final int MAX_PRACTICE_SUBGROUP_SIZE = 30;

    @Transactional
    public AutoScheduleResponse generateSchoolWideSchedule(SchoolWideScheduleRequest request) {
        List<Room> rooms = roomRepository.findAllByOrderByCapacityAsc();
        return generateScheduleWithRooms(request, rooms);
    }

    @Transactional
    public AutoScheduleResponse generateScheduleWithRooms(
            SchoolWideScheduleRequest request,
            List<Room> availableRooms) {

        int totalWeeks = request.getTotalWeeks() > 0 ? request.getTotalWeeks() : 16;

        // Sắp xếp danh sách phòng theo sức chứa tăng dần để thuật toán Best-Fit chọn phòng vừa khít nhất
        List<Room> allRooms = new ArrayList<>(availableRooms);
        allRooms.sort(Comparator.comparingInt(Room::getCapacity));

        // Quản lý phòng bận theo từng tiết trong ngày: Key = "roomCode_day_period"
        Set<String> occupiedRoomPeriods = new HashSet<>();

        // Quản lý tiết bận của giảng viên: Key = "teacherId_day_period"
        Set<String> occupiedTeacherPeriods = new HashSet<>();

        // Quản lý các ngày giảng viên đã dạy một môn học cụ thể để gom lịch vào 1-2 ngày: Key = "teacherId_courseCode" -> LinkedHashSet<Day>
        Map<String, Set<Integer>> teacherCourseDaysMap = new HashMap<>();

        // Lưu danh sách môn học theo từng giảng viên để ghi xuống MySQL
        Map<String, List<TeachingCourseDTO>> coursesByTeacher = new LinkedHashMap<>();
        Map<String, String> teacherNames = new HashMap<>();

        // Đếm thứ tự lớp mở theo từng mã môn
        Map<String, Integer> courseClassCounter = new HashMap<>();

        List<GeneratedClassSlot> allGeneratedSlots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalPhysicalClassesScheduled = 0;

        for (CourseDemandDTO course : request.getCourses()) {
            List<TeacherCandidateDTO> teachers = course.getTeachers();
            if (teachers == null || teachers.isEmpty()) {
                warnings.add("Môn " + course.getCourseName() + " không có giảng viên phụ trách!");
                continue;
            }

            int classSize = course.getClassSize() > 0 ? course.getClassSize() : 50;
            int startWeek = course.getStartWeek() > 0 ? course.getStartWeek() : 1;
            int numPeriods = course.getNumPeriodsPerSession() > 0 ? course.getNumPeriodsPerSession() : 3;
            boolean needPractice = course.getHasPractice() == null || course.getHasPractice();
            String cohort = (course.getCohort() != null && !course.getCohort().isBlank()) ? course.getCohort() : "2024";

            String theoryWeeks = buildWeekString(startWeek, totalWeeks, totalWeeks);
            String practiceWeeks = buildWeekString(Math.min(startWeek + 1, totalWeeks), totalWeeks, totalWeeks);

            Map<String, Integer> teacherClassQuota = allocateClassesToTeachers(course.getTotalClassesToOpen(), teachers);

            for (TeacherCandidateDTO teacher : teachers) {
                String teacherId = teacher.getTeacherId();
                int classesToTeach = teacherClassQuota.getOrDefault(teacherId, 0);
                if (classesToTeach <= 0) continue;

                teacherNames.put(teacherId, teacher.getTeacherName());
                coursesByTeacher.putIfAbsent(teacherId, new ArrayList<>());

                String teacherCourseKey = teacherId + "_" + course.getCourseCode();
                teacherCourseDaysMap.putIfAbsent(teacherCourseKey, new LinkedHashSet<>());
                Set<Integer> preferredDays = teacherCourseDaysMap.get(teacherCourseKey);

                List<BusyTimeSlot> externalBusySlots = getExternalBusySlots(
                        teacherId, request.getTargetSchool().getSchoolId()
                );

                for (int c = 0; c < classesToTeach; c++) {
                    int classSeq = courseClassCounter.getOrDefault(course.getCourseCode(), 0) + 1;
                    courseClassCounter.put(course.getCourseCode(), classSeq);
                    String classGroupCode = String.format("Lớp %02d", classSeq);

                    // =========================================================================
                    // 1. XẾP PHÒNG LÝ THUYẾT (Cả lớp học chung 1 phòng Lý thuyết đủ sức chứa)
                    // Ưu tiên đúng Thứ & Tiết bắt đầu trong file Excel, và gom trong 1-2 ngày của GV
                    // =========================================================================
                    RoomSlotPlacement theoryPlacement = findBestRoomAndTime(
                            allRooms,
                            occupiedRoomPeriods,
                            occupiedTeacherPeriods,
                            teacherId,
                            externalBusySlots,
                            request.getTargetSchool(),
                            false, // Phòng Lý thuyết (isLab = false)
                            classSize,
                            course.getFixedDayOfWeek(),
                            course.getFixedStartPeriod(),
                            numPeriods,
                            preferredDays,
                            -1, -1
                    );

                    if (theoryPlacement == null) {
                        warnings.add(String.format("Không tìm được phòng Lý thuyết trống đủ sức chứa (%d SV) cho môn %s (%s) - %s.",
                                classSize, course.getCourseName(), course.getCourseCode(), classGroupCode));
                        continue;
                    }

                    // Đánh dấu phòng & giảng viên bận ở khung giờ Lý thuyết
                    bookRoomAndTeacher(
                            theoryPlacement.room.getRoomCode(),
                            teacherId,
                            theoryPlacement.day,
                            theoryPlacement.startPeriod,
                            theoryPlacement.numPeriods,
                            occupiedRoomPeriods,
                            occupiedTeacherPeriods
                    );
                    preferredDays.add(theoryPlacement.day);

                    String theoryNote = String.format("Lý thuyết Cả lớp (%d SV) - Phòng %s (Sức chứa %d)",
                            classSize, theoryPlacement.room.getRoomCode(), theoryPlacement.room.getCapacity());

                    recordClassSlot(
                            allGeneratedSlots,
                            coursesByTeacher.get(teacherId),
                            teacher,
                            course,
                            cohort,
                            classGroupCode,
                            "Cả lớp",
                            "THEORY",
                            classSize,
                            theoryPlacement.room,
                            theoryPlacement.day,
                            theoryPlacement.startPeriod,
                            theoryPlacement.numPeriods,
                            theoryWeeks,
                            theoryNote,
                            allRooms.size()
                    );
                    totalPhysicalClassesScheduled++;

                    // =========================================================================
                    // 2. CHIA TỔ & XẾP PHÒNG THỰC HÀNH (Tách lớp thành nhiều Tổ, mỗi Tổ 1 phòng LAB)
                    // =========================================================================
                    if (needPractice) {
                        int numSubGroups = Math.max(2, (int) Math.ceil((double) classSize / MAX_PRACTICE_SUBGROUP_SIZE));
                        int baseSubSize = classSize / numSubGroups;
                        int remainder = classSize % numSubGroups;

                        for (int subIdx = 1; subIdx <= numSubGroups; subIdx++) {
                            int subGroupSize = baseSubSize + (subIdx <= remainder ? 1 : 0);
                            String subGroupLabel = "Tổ " + subIdx;

                            // Tìm phòng LAB cho từng Tổ:
                            // - Ưu tiên xếp cùng trong 1-2 ngày dạy của giảng viên (preferredDays)
                            // - Không trùng với tiết học Lý thuyết của chính lớp đó
                            // - Các Tổ khác nhau sẽ tự động vào các phòng LAB khác nhau hoặc ca khác nhau
                            RoomSlotPlacement practicePlacement = findBestRoomAndTime(
                                    allRooms,
                                    occupiedRoomPeriods,
                                    occupiedTeacherPeriods,
                                    teacherId,
                                    externalBusySlots,
                                    request.getTargetSchool(),
                                    true, // Phòng Thực hành LAB (isLab = true)
                                    subGroupSize,
                                    null, // Thực hành linh hoạt ca trong 1-2 ngày ưu tiên của GV
                                    null,
                                    numPeriods,
                                    preferredDays,
                                    theoryPlacement.day,
                                    theoryPlacement.startPeriod
                            );

                            if (practicePlacement != null) {
                                bookRoomAndTeacher(
                                        practicePlacement.room.getRoomCode(),
                                        teacherId,
                                        practicePlacement.day,
                                        practicePlacement.startPeriod,
                                        practicePlacement.numPeriods,
                                        occupiedRoomPeriods,
                                        occupiedTeacherPeriods
                                );
                                preferredDays.add(practicePlacement.day);

                                String practiceNote = String.format("Thực hành %s (%d/%d SV) - Phòng LAB %s (Sức chứa %d)",
                                        subGroupLabel, subGroupSize, classSize,
                                        practicePlacement.room.getRoomCode(), practicePlacement.room.getCapacity());

                                recordClassSlot(
                                        allGeneratedSlots,
                                        coursesByTeacher.get(teacherId),
                                        teacher,
                                        course,
                                        cohort,
                                        classGroupCode + " - " + subGroupLabel,
                                        subGroupLabel,
                                        "PRACTICE",
                                        subGroupSize,
                                        practicePlacement.room,
                                        practicePlacement.day,
                                        practicePlacement.startPeriod,
                                        practicePlacement.numPeriods,
                                        practiceWeeks,
                                        practiceNote,
                                        allRooms.size()
                                );
                                totalPhysicalClassesScheduled++;
                            } else {
                                warnings.add(String.format("Môn %s (%s - %s, %d SV): Hết phòng LAB thực hành trống phù hợp!",
                                        course.getCourseName(), classGroupCode, subGroupLabel, subGroupSize));
                            }
                        }
                    }
                }
            }
        }

        // Lưu toàn bộ lịch của từng giảng viên xuống MySQL
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
                .schoolId(request.getTargetSchool().getSchoolId())
                .schoolName(request.getTargetSchool().getSchoolName())
                .semesterStartDate(request.getSemesterStartDate())
                .periods(request.getTargetSchool().getPeriods())
                .status(warnings.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS")
                .message("Đã hoàn tất xếp lịch tự động: Chia đều lớp cho Giảng viên (gom gọn 1-2 ngày), chọn phòng theo Sĩ số và tách Tổ Thực hành!")
                .totalRoomsAvailable(allRooms.size())
                .totalClassesScheduled(totalPhysicalClassesScheduled)
                .scheduledClasses(allGeneratedSlots)
                .unscheduledWarnings(warnings)
                .build();
    }

    private record RoomSlotPlacement(Room room, int day, int startPeriod, int numPeriods) {}

    /**
     * Tìm Phòng học (Best-Fit theo sĩ số) và Khung giờ:
     * 1. Nếu file Kế hoạch mở lớp đã có Thứ (fixedDay) và Tiết BĐ (fixedStartPeriod): Tìm phòng trống ngay tại Thứ & Tiết đó trước.
     * 2. Ưu tiên xếp vào các ngày giảng viên đã lên trường dạy môn này (preferredDays, tối đa 1-2 ngày) trước khi mở sang ngày mới.
     */
    private RoomSlotPlacement findBestRoomAndTime(
            List<Room> allRooms,
            Set<String> occupiedRoomPeriods,
            Set<String> occupiedTeacherPeriods,
            String teacherId,
            List<BusyTimeSlot> externalBusySlots,
            SchoolConfigDTO targetSchool,
            boolean needLabRoom,
            int requiredCapacity,
            Integer fixedDay,
            Integer fixedStartPeriod,
            int numPeriods,
            Set<Integer> preferredDays,
            int avoidDay,
            int avoidStartPeriod) {

        // Xây dựng thứ tự ưu tiên các ngày kiểm tra:
        // 1. Ngày cố định từ file Excel (nếu có) -> 2. Ngày mà GV đang dạy môn này (gom vào 1-2 ngày) -> 3. Các ngày còn lại trong tuần
        List<Integer> candidateDays = new ArrayList<>();
        if (fixedDay != null && fixedDay >= 2 && fixedDay <= 8) {
            candidateDays.add(fixedDay);
        }
        for (Integer pd : preferredDays) {
            if (!candidateDays.contains(pd)) candidateDays.add(pd);
        }
        for (int d = 2; d <= 7; d++) {
            if (!candidateDays.contains(d)) candidateDays.add(d);
        }

        // Xây dựng thứ tự ưu tiên Tiết bắt đầu:
        List<Integer> candidateStartPeriods = new ArrayList<>();
        if (fixedStartPeriod != null && fixedStartPeriod >= 1 && fixedStartPeriod <= 12) {
            candidateStartPeriods.add(fixedStartPeriod);
        }
        for (int sp : DEFAULT_START_PERIODS) {
            if (!candidateStartPeriods.contains(sp)) candidateStartPeriods.add(sp);
        }

        for (int day : candidateDays) {
            for (int startP : candidateStartPeriods) {
                // Không xếp Thực hành đè lên đúng giờ Lý thuyết của chính lớp đó
                if (day == avoidDay && startP == avoidStartPeriod) {
                    continue;
                }

                // Kiểm tra giảng viên có rảnh trong toàn bộ các tiết (startP .. startP + numPeriods - 1) không
                if (!isTeacherFree(teacherId, day, startP, numPeriods, occupiedTeacherPeriods)) {
                    continue;
                }
                if (isTeacherBusyExternally(startP, numPeriods, day, targetSchool, externalBusySlots)) {
                    continue;
                }

                // Tìm phòng trống đúng loại (Lý thuyết / LAB) và có sức chứa vừa vặn nhất (Best-Fit)
                for (Room room : allRooms) {
                    if (room.isLab() != needLabRoom) continue;
                    if (room.getCapacity() < requiredCapacity) continue;

                    if (isRoomFree(room.getRoomCode(), day, startP, numPeriods, occupiedRoomPeriods)) {
                        return new RoomSlotPlacement(room, day, startP, numPeriods);
                    }
                }
            }
        }
        return null;
    }

    private boolean isRoomFree(String roomCode, int day, int startPeriod, int numPeriods, Set<String> occupiedRoomPeriods) {
        for (int p = startPeriod; p < startPeriod + numPeriods; p++) {
            if (occupiedRoomPeriods.contains(roomCode + "_" + day + "_" + p)) {
                return false;
            }
        }
        return true;
    }

    private boolean isTeacherFree(String teacherId, int day, int startPeriod, int numPeriods, Set<String> occupiedTeacherPeriods) {
        for (int p = startPeriod; p < startPeriod + numPeriods; p++) {
            if (occupiedTeacherPeriods.contains(teacherId + "_" + day + "_" + p)) {
                return false;
            }
        }
        return true;
    }

    private void bookRoomAndTeacher(
            String roomCode,
            String teacherId,
            int day,
            int startPeriod,
            int numPeriods,
            Set<String> occupiedRoomPeriods,
            Set<String> occupiedTeacherPeriods) {
        for (int p = startPeriod; p < startPeriod + numPeriods; p++) {
            occupiedRoomPeriods.add(roomCode + "_" + day + "_" + p);
            occupiedTeacherPeriods.add(teacherId + "_" + day + "_" + p);
        }
    }

    private Map<String, Integer> allocateClassesToTeachers(int totalClasses, List<TeacherCandidateDTO> teachers) {
        Map<String, Integer> allocation = new LinkedHashMap<>();
        int base = totalClasses / teachers.size();
        int mod = totalClasses % teachers.size();
        for (int i = 0; i < teachers.size(); i++) {
            int count = base + (i < mod ? 1 : 0);
            allocation.put(teachers.get(i).getTeacherId(), Math.max(1, count));
        }
        return allocation;
    }

    private void recordClassSlot(
            List<GeneratedClassSlot> outputList,
            List<TeachingCourseDTO> dbDtoList,
            TeacherCandidateDTO teacher,
            CourseDemandDTO course,
            String cohort,
            String groupName,
            String subGroup,
            String sessionType,
            int studentCount,
            Room room,
            int day,
            int startPeriod,
            int numPeriods,
            String weekSchedule,
            String phaseNote,
            int totalRoomsAvailable) {

        String typeTag = "PRACTICE".equals(sessionType) ? "[TH] " : "[LT] ";
        String displayCourseName = String.format("%s%s (Khóa %s - %s - %d SV)",
                typeTag, course.getCourseName(), cohort, groupName, studentCount);

        outputList.add(GeneratedClassSlot.builder()
                .baseCourseName(course.getCourseName())
                .credits(course.getCredits())
                .teacherId(teacher.getTeacherId())
                .teacherName(teacher.getTeacherName())
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .cohort(cohort)
                .classGroup(groupName)
                .subGroup(subGroup)
                .sessionType(sessionType)
                .studentCount(studentCount)
                .room(room.getRoomCode())
                .roomType(room.getRoomType())
                .roomCapacity(room.getCapacity())
                .dayOfWeek(day)
                .startPeriod(startPeriod)
                .numPeriods(numPeriods)
                .weekSchedule(weekSchedule)
                .phaseNote(phaseNote)
                .totalRoomsAvailable(totalRoomsAvailable)
                .totalClassesScheduled(outputList.size() + 1)
                .build());

        dbDtoList.add(TeachingCourseDTO.builder()
                .courseCode(course.getCourseCode())
                .courseName(displayCourseName)
                .room(room.getRoomCode())
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

    private String buildWeekString(int fromWeek, int toWeek, int totalWeeks) {
        StringBuilder sb = new StringBuilder();
        for (int w = 1; w <= totalWeeks; w++) {
            sb.append((w >= fromWeek && w <= toWeek) ? '1' : '-');
        }
        return sb.toString();
    }

    private record BusyTimeSlot(int dayOfWeek, LocalTime start, LocalTime end) {}
}
