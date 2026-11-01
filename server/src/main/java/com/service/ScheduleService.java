package com.service;

import com.dto.*;
import com.model.*;
import com.repository.RoomRepository;
import com.repository.SchoolRepository;
import com.repository.TeacherRepository;
import com.repository.TeacherSubjectRepository;
import com.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final RoomRepository roomRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.security.ScheduleAccess scheduleAccess;
    private final ScheduleWriteLock writeLock;

    // Bảng 12 màu sắc tương phản rõ rệt để mỗi giảng viên có 1 màu riêng trên lịch toàn khoa
    private static final String[] TEACHER_PALETTE = {
        "#2563eb", "#16a34a", "#9333ea", "#ea580c", "#0284c7", "#db2777",
        "#4f46e5", "#0d9488", "#d97706", "#dc2626", "#7c3aed", "#059669"
    };

    private String pickTeacherColor(String teacherId, int index) {
        if (index >= 0) {
            return TEACHER_PALETTE[index % TEACHER_PALETTE.length];
        }
        int hash = Math.abs(teacherId != null ? teacherId.hashCode() : 0);
        return TEACHER_PALETTE[hash % TEACHER_PALETTE.length];
    }

    @Transactional
    public void saveOrUpdateSchoolSchedule(SchoolUploadRequest request) {
        ScheduleValidation.upload(request);
        scheduleAccess.requireTeacherAccess(request.getTeacherId());
        writeLock.acquire();

        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseGet(() -> Teacher.builder()
                        .teacherId(request.getTeacherId())
                        .fullName(request.getTeacherName() != null ? request.getTeacherName() : request.getTeacherId())
                        .semesterStartDate(request.getSemesterStartDate() != null ? request.getSemesterStartDate() : LocalDate.of(2026, 8, 10))
                        .build());

        if (request.getSemesterStartDate() != null) {
            teacher.setSemesterStartDate(request.getSemesterStartDate());
        }
        teacherRepository.save(teacher);

        String schoolCode = request.getSchool().getSchoolId();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacher.getTeacherId(), schoolCode)
                .orElseGet(() -> School.builder()
                        .schoolCode(schoolCode)
                        .teacher(teacher)
                        .build());

        school.setSchoolName(request.getSchool().getSchoolName());
        String assignedColor = pickTeacherColor(teacher.getTeacherId(), -1);
        school.setColorCode(assignedColor);

        if (school.getPeriods() == null) {
            school.setPeriods(new HashSet<>());
        } else {
            school.getPeriods().clear();
        }

        if (request.getSchool().getPeriods() != null) {
            for (PeriodTimeDTO pt : request.getSchool().getPeriods()) {
                school.getPeriods().add(SchoolPeriod.builder()
                        .periodNumber(pt.getPeriod())
                        .startTime(pt.getStart())
                        .endTime(pt.getEnd())
                        .school(school)
                        .build());
            }
        }

        if (school.getCourses() == null) {
            school.setCourses(new HashSet<>());
        } else {
            school.getCourses().clear();
        }

        if (request.getCourses() != null) {
            for (TeachingCourseDTO dto : request.getCourses()) {
                school.getCourses().add(TeachingCourse.builder()
                        .courseCode(dto.getCourseCode())
                        .sourceType(dto.getSourceType() == null ? "EXCEL_UPLOAD" : dto.getSourceType())
                        .scheduleClassId(dto.getScheduleClassId())
                        .scheduleParentId(dto.getScheduleParentId())
                        .sessionType(dto.getSessionType())
                        .planSnapshot(dto.getPlanSnapshot())
                        .courseName(dto.getCourseName())
                        .classroom(dto.getRoom())
                        .dayOfWeek(dto.getDayOfWeek())
                        .startPeriod(dto.getStartPeriod())
                        .numPeriods(dto.getNumPeriods())
                        .weekSchedule(dto.getWeekSchedule())
                        .customStartTime(dto.getCustomStartTime())
                        .customEndTime(dto.getCustomEndTime())
                        .school(school)
                        .build());
            }
        }

        schoolRepository.save(school);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceWeeklyGenerated(SchoolWideScheduleRequest request, Map<String, List<TeachingCourseDTO>> generated,
                                       Map<String, String> names) {
        writeLock.acquire();
        String schoolCode = request.getTargetSchool().getSchoolId();
        var existing = teacherRepository.findAllWithFullSchedule();
        var retained = new LinkedHashMap<String, List<TeachingCourseDTO>>();
        for (var teacher : existing) for (var school : teacher.getSchools()) {
            if (!schoolCode.equals(school.getSchoolCode())) continue;
            boolean managed = school.getCourses().stream().anyMatch(c -> "WEEKLY_AUTO".equals(c.getSourceType()));
            if (!managed && !generated.containsKey(teacher.getTeacherId())) continue;
            var rows = new ArrayList<TeachingCourseDTO>();
            for (var c : school.getCourses()) if (!"WEEKLY_AUTO".equals(c.getSourceType())) {
                rows.add(TeachingCourseDTO.builder().courseCode(c.getCourseCode()).courseName(c.getCourseName())
                        .room(c.getClassroom()).dayOfWeek(c.getDayOfWeek()).startPeriod(c.getStartPeriod())
                        .numPeriods(c.getNumPeriods()).weekSchedule(c.getWeekSchedule()).customStartTime(c.getCustomStartTime())
                        .customEndTime(c.getCustomEndTime()).sourceType(c.getSourceType()).scheduleClassId(c.getScheduleClassId())
                        .scheduleParentId(c.getScheduleParentId()).sessionType(c.getSessionType()).planSnapshot(c.getPlanSnapshot()).build());
            }
            retained.put(teacher.getTeacherId(), rows);
            names.putIfAbsent(teacher.getTeacherId(), teacher.getFullName());
        }
        generated.forEach((id, rows) -> retained.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(rows));
        for (var entry : retained.entrySet()) {
            if (entry.getValue().isEmpty()) deleteSchoolSchedule(entry.getKey(), schoolCode);
            else saveOrUpdateSchoolSchedule(SchoolUploadRequest.builder().teacherId(entry.getKey()).teacherName(names.get(entry.getKey()))
                    .semesterStartDate(request.getSemesterStartDate()).school(request.getTargetSchool()).courses(entry.getValue()).build());
        }
    }

    @Transactional
    public void deleteSchoolSchedule(String teacherId, String schoolCode) {
        if ("ALL".equalsIgnoreCase(teacherId) || "KHOA".equalsIgnoreCase(teacherId) || teacherId == null || teacherId.isBlank()) {
            deleteEntireDepartmentSchedule(schoolCode);
            return;
        }
        scheduleAccess.requireTeacherAccess(teacherId);
        writeLock.acquire();
        School school = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch của đơn vị " + schoolCode + " cho giảng viên " + teacherId));

        Teacher teacher = school.getTeacher();
        teacher.getSchools().remove(school);
        schoolRepository.delete(school);
    }

    @Transactional
    public int deleteEntireDepartmentSchedule(String schoolCode) {
        writeLock.acquire();
        List<Teacher> allTeachers = teacherRepository.findAllWithFullSchedule();
        int removedCount = 0;
        for (Teacher teacher : allTeachers) {
            if (teacher.getSchools() == null) continue;
            List<School> toRemove = teacher.getSchools().stream()
                    .filter(s -> schoolCode == null || schoolCode.isBlank() || "all".equalsIgnoreCase(schoolCode) || s.getSchoolCode().equalsIgnoreCase(schoolCode))
                    .toList();
            for (School s : toRemove) {
                teacher.getSchools().remove(s);
                schoolRepository.delete(s);
                removedCount++;
            }
        }
        return removedCount;
    }

    @Transactional(readOnly = true)
    public WeekScheduleResponse getWeeklySchedule(String teacherId, int weekIndex) {
        boolean isAllDepartment = "ALL".equalsIgnoreCase(teacherId) || "KHOA".equalsIgnoreCase(teacherId) || teacherId == null || teacherId.isBlank();
        if (!isAllDepartment) {
            scheduleAccess.requireTeacherAccess(teacherId);
        }
        if (weekIndex < 1 || weekIndex > 100) throw new IllegalArgumentException("Tuần phải từ 1 đến 100.");

        List<Teacher> teachers;
        if (isAllDepartment) {
            teachers = teacherRepository.findAllWithFullSchedule().stream()
                    .sorted(Comparator.comparing(Teacher::getTeacherId))
                    .toList();
        } else {
            Teacher single = teacherRepository.findTeacherWithFullSchedule(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dữ liệu giảng viên: " + teacherId));
            teachers = List.of(single);
        }

        LocalDate mondayOfWeek1 = teachers.stream()
                .map(Teacher::getSemesterStartDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(LocalDate.of(2026, 8, 10));
        ScheduleValidation.monday(mondayOfWeek1);

        LocalDate targetMonday = mondayOfWeek1.plusWeeks(weekIndex - 1);
        LocalDate targetSunday = targetMonday.plusDays(6);

        List<LessonSlot> weeklyLessons = new ArrayList<>();
        Map<String, SchoolConfigDTO> schoolConfigs = new TreeMap<>();
        Set<Object> uniqueLessonCheck = new HashSet<>();

        Map<String, String> teacherColorMap = new LinkedHashMap<>();
        int colorCounter = 0;
        for (Teacher t : teachers) {
            boolean hasCourses = t.getSchools() != null && t.getSchools().stream()
                    .anyMatch(s -> s.getCourses() != null && !s.getCourses().isEmpty());
            if (hasCourses) {
                teacherColorMap.put(t.getTeacherId(), TEACHER_PALETTE[colorCounter % TEACHER_PALETTE.length]);
                colorCounter++;
            }
        }

        for (Teacher teacher : teachers) {
            String teacherDistinctColor = teacherColorMap.getOrDefault(
                    teacher.getTeacherId(),
                    pickTeacherColor(teacher.getTeacherId(), -1)
            );
            if (teacher.getSchools() == null) continue;

            for (School school : teacher.getSchools()) {
                schoolConfigs.computeIfAbsent(school.getSchoolCode(), code -> SchoolConfigDTO.builder()
                        .schoolId(code)
                        .schoolName(school.getSchoolName())
                        .colorCode(teacherDistinctColor)
                        .periods(school.getPeriods() == null ? List.of() : school.getPeriods().stream()
                                .sorted(Comparator.comparingInt(SchoolPeriod::getPeriodNumber))
                                .map(period -> new PeriodTimeDTO(period.getPeriodNumber(), period.getStartTime(), period.getEndTime()))
                                .toList())
                        .build());

                if (school.getCourses() == null) continue;
                for (TeachingCourse course : school.getCourses()) {
                    String weekStr = course.getWeekSchedule();
                    if (weekStr == null || weekStr.isBlank()) continue;

                    int charIndex = weekIndex - 1;
                    if (charIndex >= 0 && charIndex < weekStr.length()) {
                        char flag = weekStr.charAt(charIndex);
                        if (flag != '-' && flag != ' ') {
                            int endPeriod = course.getStartPeriod() + course.getNumPeriods() - 1;

                            LocalTime startTime = (course.getCustomStartTime() != null)
                                    ? course.getCustomStartTime()
                                    : getPeriodStartTime(school, course.getStartPeriod());
                            LocalTime endTime = (course.getCustomEndTime() != null)
                                    ? course.getCustomEndTime()
                                    : getPeriodEndTime(school, endPeriod);

                            int offsetDays = (course.getDayOfWeek() == 8) ? 6 : (course.getDayOfWeek() - 2);
                            LocalDate classDate = targetMonday.plusDays(offsetDays);

                            Object uniqueKey = course.getId() != null ? course.getId() : course;

                            if (!uniqueLessonCheck.contains(uniqueKey)) {
                                uniqueLessonCheck.add(uniqueKey);

                                String teacherLabel = teacher.getFullName() != null && !teacher.getFullName().isBlank()
                                        ? teacher.getFullName() + " (" + teacher.getTeacherId() + ")"
                                        : teacher.getTeacherId();

                                String displayCourse = isAllDepartment
                                        ? "[" + teacher.getTeacherId() + " - " + teacher.getFullName() + "] " + course.getCourseName()
                                        : course.getCourseName();

                                weeklyLessons.add(LessonSlot.builder()
                                        .schoolId(school.getSchoolCode())
                                        .schoolName(isAllDepartment ? "GV: " + teacherLabel : school.getSchoolName())
                                        .colorCode(teacherDistinctColor)
                                        .courseName(displayCourse)
                                        .room(course.getClassroom())
                                        .dayOfWeek(course.getDayOfWeek())
                                        .date(classDate)
                                        .startPeriod(course.getStartPeriod())
                                        .endPeriod(endPeriod)
                                        .startTime(startTime)
                                        .endTime(endTime)
                                        .isConflict(false)
                                        .build());
                            }
                        }
                    }
                }
            }
        }

        if (!isAllDepartment) {
            detectConflicts(weeklyLessons);
        }
        weeklyLessons.sort(Comparator.comparing(LessonSlot::getDate).thenComparing(LessonSlot::getStartTime));

        return WeekScheduleResponse.builder()
                .teacherId(teacherId)
                .weekIndex(weekIndex)
                .startDate(targetMonday)
                .endDate(targetSunday)
                .schools(new ArrayList<>(schoolConfigs.values()))
                .lessons(weeklyLessons)
                .build();
    }

    // ==================== XẾP LỊCH BỔ SUNG CHO GIẢNG VIÊN THỈNH GIẢNG ====================
    @Transactional(rollbackFor = Exception.class)
    public AutoScheduleResponse scheduleVisitingTeacher(VisitingTeacherRequest req) {
        writeLock.acquire();
        String schoolCode = (req.getSchoolId() == null || req.getSchoolId().isBlank()) ? "PTIT" : req.getSchoolId().trim();
        String schoolName = (req.getSchoolName() == null || req.getSchoolName().isBlank()) ? "Khoa Công nghệ Thông tin - PTIT" : req.getSchoolName().trim();
        LocalDate semesterStart = req.getSemesterStartDate() != null ? req.getSemesterStartDate() : LocalDate.of(2026, 8, 10);
        ScheduleValidation.monday(semesterStart);

        if (req.getTeacherId() == null || req.getTeacherId().isBlank() || req.getTeacherName() == null || req.getTeacherName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Mã giảng viên và Tên giảng viên thỉnh giảng.");
        }
        if (req.getCourseCode() == null || req.getCourseCode().isBlank() || req.getCourseName() == null || req.getCourseName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Mã môn học và Tên môn học.");
        }

        String teacherId = req.getTeacherId().trim().toUpperCase(Locale.ROOT);
        String teacherName = req.getTeacherName().trim();
        String courseCode = req.getCourseCode().trim().toUpperCase(Locale.ROOT);
        String courseName = req.getCourseName().trim();
        String cohort = (req.getCohort() == null || req.getCohort().isBlank()) ? "2024" : req.getCohort().trim();
        String classGroup = (req.getClassGroup() == null || req.getClassGroup().isBlank()) ? "Lớp TG-01" : req.getClassGroup().trim();
        boolean isPractice = "PRACTICE".equalsIgnoreCase(req.getSessionType()) || "TH".equalsIgnoreCase(req.getSessionType());
        int studentCount = (req.getStudentCount() != null && req.getStudentCount() > 0) ? req.getStudentCount() : (isPractice ? 30 : 60);
        int requiredPeriods = (req.getRequiredPeriods() != null && req.getRequiredPeriods() > 0) ? req.getRequiredPeriods() : (isPractice ? 15 : 30);
        int periodsPerSession = (req.getPeriodsPerSession() != null && req.getPeriodsPerSession() >= 1 && req.getPeriodsPerSession() <= 5) ? req.getPeriodsPerSession() : 3;

        LocalDate studyStart = req.getStudyStartDate() != null ? req.getStudyStartDate() : semesterStart;
        LocalDate studyEnd = req.getStudyEndDate() != null ? req.getStudyEndDate() : semesterStart.plusDays(105);

        Set<Integer> busyDays = new HashSet<>(req.getBusyDaysOfWeek() != null ? req.getBusyDaysOfWeek() : List.of());
        Set<LocalDate> busySpecificDates = new HashSet<>();
        if (req.getBusyDatesText() != null && !req.getBusyDatesText().isBlank()) {
            for (String token : req.getBusyDatesText().split("[,;\\s]+")) {
                if (token.isBlank()) continue;
                try {
                    busySpecificDates.add(LocalDate.parse(token.trim()));
                } catch (Exception ignored) {}
            }
        }

        String defaultPwd = Normalizer.normalize(teacherName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT) + teacherId;

        Teacher visitingTeacher = teacherRepository.findById(teacherId).orElse(null);
        if (visitingTeacher == null) {
            visitingTeacher = Teacher.builder()
                    .teacherId(teacherId)
                    .fullName(teacherName)
                    .username(teacherId)
                    .password(passwordEncoder.encode(defaultPwd))
                    .role("TEACHER")
                    .semesterStartDate(semesterStart)
                    .build();
        } else {
            visitingTeacher.setFullName(teacherName);
            if (visitingTeacher.getUsername() == null || visitingTeacher.getUsername().isBlank()) {
                visitingTeacher.setUsername(teacherId);
            }
            if (visitingTeacher.getPassword() == null || visitingTeacher.getPassword().isBlank()) {
                visitingTeacher.setPassword(passwordEncoder.encode(defaultPwd));
            }
        }
        final Teacher finalVisitingTeacher = visitingTeacher;
        teacherRepository.save(finalVisitingTeacher);

        TeacherSubject ts = teacherSubjectRepository.findBySchoolCodeAndTeacherIdIgnoreCaseAndCourseCodeIgnoreCaseAndCohortIgnoreCase(
                schoolCode, teacherId, courseCode, cohort).orElse(new TeacherSubject());
        ts.setSchoolCode(schoolCode);
        ts.setTeacherId(teacherId);
        ts.setTeacherName(teacherName);
        ts.setCourseCode(courseCode);
        ts.setCourseName(courseName);
        ts.setCohort(cohort);
        ts.setTheoryWeight(isPractice ? 0 : 1);
        ts.setPracticeWeight(isPractice ? 1 : 0);
        teacherSubjectRepository.save(ts);

        List<Room> candidateRooms = roomRepository.findBySchoolCodeOrderByCapacityAsc(schoolCode).stream()
                .filter(r -> r.isLab() == isPractice && r.getCapacity() >= studentCount)
                .toList();
        if (candidateRooms.isEmpty()) {
            throw new IllegalArgumentException("Không có phòng " + (isPractice ? "Thực hành (LAB)" : "Lý thuyết") + " nào đủ sức chứa " + studentCount + " SV.");
        }

        List<Teacher> allTeachers = teacherRepository.findAllWithFullSchedule();
        int startWeek = Math.max(1, (int) (ChronoUnit.DAYS.between(semesterStart, studyStart) / 7) + 1);
        int endWeek = Math.min(100, (int) (ChronoUnit.DAYS.between(semesterStart, studyEnd) / 7) + 1);

        int[] candidateStartPeriods = {1, 4, 7, 10};
        int remainingPeriods = requiredPeriods;

        School visitingSchool = schoolRepository.findByTeacher_TeacherIdAndSchoolCode(teacherId, schoolCode)
                .orElseGet(() -> {
                    School s = School.builder()
                            .schoolCode(schoolCode)
                            .schoolName(schoolName)
                            .colorCode(pickTeacherColor(teacherId, -1))
                            .teacher(finalVisitingTeacher)
                            .periods(new HashSet<>())
                            .courses(new HashSet<>())
                            .build();
                    for (PeriodTimeDTO pt : default12Periods()) {
                        s.getPeriods().add(SchoolPeriod.builder()
                                .periodNumber(pt.getPeriod())
                                .startTime(pt.getStart())
                                .endTime(pt.getEnd())
                                .school(s)
                                .build());
                    }
                    return s;
                });
        if (visitingSchool.getPeriods() == null || visitingSchool.getPeriods().isEmpty()) {
            visitingSchool.setPeriods(new HashSet<>());
            for (PeriodTimeDTO pt : default12Periods()) {
                visitingSchool.getPeriods().add(SchoolPeriod.builder()
                        .periodNumber(pt.getPeriod())
                        .startTime(pt.getStart())
                        .endTime(pt.getEnd())
                        .school(visitingSchool)
                        .build());
            }
        }

        List<GeneratedClassSlot> scheduledSlots = new ArrayList<>();
        Map<String, char[]> mergedWeekMap = new LinkedHashMap<>();
        Map<String, GeneratedClassSlot> slotMetaMap = new LinkedHashMap<>();
        List<String> adjustNotes = new ArrayList<>();

        for (int w = startWeek; w <= endWeek && remainingPeriods > 0; w++) {
            int periodsThisWeek = 0;
            for (int day = 2; day <= 7 && remainingPeriods > 0 && periodsThisWeek < 6; day++) {
                if (busyDays.contains(day)) continue;
                LocalDate currentDate = semesterStart.plusDays((w - 1) * 7L + (day - 2));
                if (currentDate.isBefore(studyStart) || currentDate.isAfter(studyEnd)) continue;
                if (busySpecificDates.contains(currentDate)) continue;

                for (int startP : candidateStartPeriods) {
                    if (remainingPeriods <= 0 || periodsThisWeek >= 6) break;
                    int countP = Math.min(periodsPerSession, remainingPeriods);

                    if (isTeacherBusyAt(visitingSchool, w, day, startP, countP)) continue;

                    Room chosenRoom = null;
                    for (Room room : candidateRooms) {
                        if (!isRoomOccupiedAt(allTeachers, schoolCode, room.getRoomCode(), w, day, startP, countP)) {
                            chosenRoom = room;
                            break;
                        }
                    }

                    if (chosenRoom == null) {
                        for (Room room : candidateRooms) {
                            if (tryRelocateExistingCourse(allTeachers, candidateRooms, schoolCode, room.getRoomCode(), w, day, startP, countP, adjustNotes)) {
                                chosenRoom = room;
                                break;
                            }
                        }
                    }

                    if (chosenRoom != null) {
                        String key = chosenRoom.getRoomCode() + ":" + day + ":" + startP + ":" + countP;
                        char[] weekChars = mergedWeekMap.computeIfAbsent(key, k -> {
                            char[] arr = new char[endWeek];
                            Arrays.fill(arr, '-');
                            return arr;
                        });
                        weekChars[w - 1] = '1';

                        String displayCourseName = (isPractice ? "[TH] " : "[LT] ") + courseName + " (Khóa " + cohort + " - " + classGroup + " - Thỉnh giảng)";
                        final Room finalRoom = chosenRoom;
                        final int finalDay = day;
                        slotMetaMap.computeIfAbsent(key, k -> GeneratedClassSlot.builder()
                                .teacherId(teacherId)
                                .teacherName(teacherName)
                                .courseCode(courseCode)
                                .courseName(displayCourseName)
                                .cohort(cohort)
                                .classGroup(classGroup)
                                .sessionType(isPractice ? "PRACTICE" : "THEORY")
                                .studentCount(studentCount)
                                .room(finalRoom.getRoomCode())
                                .roomType(finalRoom.getRoomType())
                                .roomCapacity(finalRoom.getCapacity())
                                .dayOfWeek(finalDay)
                                .startPeriod(startP)
                                .numPeriods(countP)
                                .build());

                        remainingPeriods -= countP;
                        periodsThisWeek += countP;
                        break;
                    }
                }
            }
        }

        if (mergedWeekMap.isEmpty()) {
            throw new IllegalArgumentException("Không tìm được phòng hoặc khung giờ trống ngoài các ngày bận của giảng viên thỉnh giảng.");
        }

        for (var entry : mergedWeekMap.entrySet()) {
            String key = entry.getKey();
            String weekScheduleStr = new String(entry.getValue()).replaceFirst("-+$", "");
            GeneratedClassSlot slot = slotMetaMap.get(key);
            slot.setWeekSchedule(weekScheduleStr);
            slot.setPhaseNote(adjustNotes.isEmpty() ? "Xếp lịch thỉnh giảng" : String.join("; ", adjustNotes));
            scheduledSlots.add(slot);

            visitingSchool.getCourses().add(TeachingCourse.builder()
                    .courseCode(slot.getCourseCode())
                    .courseName(slot.getCourseName())
                    .classroom(slot.getRoom())
                    .dayOfWeek(slot.getDayOfWeek())
                    .startPeriod(slot.getStartPeriod())
                    .numPeriods(slot.getNumPeriods())
                    .weekSchedule(weekScheduleStr)
                    .sessionType(slot.getSessionType())
                    .sourceType("VISITING_AUTO")
                    .school(visitingSchool)
                    .build());
        }

        schoolRepository.save(visitingSchool);

        List<String> warnings = new ArrayList<>();
        if (remainingPeriods > 0) {
            warnings.add("Còn thiếu " + remainingPeriods + " tiết chưa xếp hết do giới hạn ngày bận.");
        }

        return AutoScheduleResponse.builder()
                .schoolId(schoolCode)
                .schoolName(schoolName)
                .semesterStartDate(semesterStart)
                .periods(default12Periods())
                .saved(true)
                .status("SUCCESS")
                .message("Đã xếp lịch thành công cho giảng viên thỉnh giảng " + teacherName + " (" + teacherId + ")"
                        + (adjustNotes.isEmpty() ? "." : " — Đã tự động điều chỉnh lịch cũ: " + String.join(", ", adjustNotes)))
                .totalRoomsAvailable(candidateRooms.size())
                .totalClassesScheduled(scheduledSlots.size())
                .scheduledClasses(scheduledSlots)
                .unscheduledWarnings(warnings)
                .build();
    }

    private boolean isTeacherBusyAt(School school, int week, int day, int startP, int countP) {
        if (school.getCourses() == null) return false;
        int endP = startP + countP - 1;
        for (TeachingCourse c : school.getCourses()) {
            if (c.getDayOfWeek() != day || c.getWeekSchedule() == null || week - 1 >= c.getWeekSchedule().length()) continue;
            char ch = c.getWeekSchedule().charAt(week - 1);
            if (ch != '-' && ch != ' ') {
                int cEnd = c.getStartPeriod() + c.getNumPeriods() - 1;
                if (Math.max(startP, c.getStartPeriod()) <= Math.min(endP, cEnd)) return true;
            }
        }
        return false;
    }

    private boolean isRoomOccupiedAt(List<Teacher> allTeachers, String schoolCode, String roomCode, int week, int day, int startP, int countP) {
        int endP = startP + countP - 1;
        for (Teacher t : allTeachers) {
            if (t.getSchools() == null) continue;
            for (School s : t.getSchools()) {
                if (!schoolCode.equalsIgnoreCase(s.getSchoolCode()) || s.getCourses() == null) continue;
                for (TeachingCourse c : s.getCourses()) {
                    if (!roomCode.equalsIgnoreCase(c.getClassroom()) || c.getDayOfWeek() != day) continue;
                    if (c.getWeekSchedule() == null || week - 1 >= c.getWeekSchedule().length()) continue;
                    char ch = c.getWeekSchedule().charAt(week - 1);
                    if (ch != '-' && ch != ' ') {
                        int cEnd = c.getStartPeriod() + c.getNumPeriods() - 1;
                        if (Math.max(startP, c.getStartPeriod()) <= Math.min(endP, cEnd)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tryRelocateExistingCourse(
            List<Teacher> allTeachers,
            List<Room> candidateRooms,
            String schoolCode,
            String targetRoomCode,
            int week,
            int day,
            int startP,
            int countP,
            List<String> adjustNotes) {
        int endP = startP + countP - 1;
        for (Teacher t : allTeachers) {
            if (t.getSchools() == null) continue;
            for (School s : t.getSchools()) {
                if (!schoolCode.equalsIgnoreCase(s.getSchoolCode()) || s.getCourses() == null) continue;
                for (TeachingCourse c : s.getCourses()) {
                    if (!targetRoomCode.equalsIgnoreCase(c.getClassroom()) || c.getDayOfWeek() != day) continue;
                    if (c.getWeekSchedule() == null || week - 1 >= c.getWeekSchedule().length()) continue;
                    char ch = c.getWeekSchedule().charAt(week - 1);
                    if (ch != '-' && ch != ' ') {
                        int cEnd = c.getStartPeriod() + c.getNumPeriods() - 1;
                        if (Math.max(startP, c.getStartPeriod()) <= Math.min(endP, cEnd)) {
                            for (Room altRoom : candidateRooms) {
                                if (altRoom.getRoomCode().equalsIgnoreCase(targetRoomCode)) continue;
                                boolean freeAllWeeks = true;
                                for (int wIdx = 0; wIdx < c.getWeekSchedule().length(); wIdx++) {
                                    if (c.getWeekSchedule().charAt(wIdx) != '-' && c.getWeekSchedule().charAt(wIdx) != ' ') {
                                        if (isRoomOccupiedAt(allTeachers, schoolCode, altRoom.getRoomCode(), wIdx + 1, c.getDayOfWeek(), c.getStartPeriod(), c.getNumPeriods())) {
                                            freeAllWeeks = false;
                                            break;
                                        }
                                    }
                                }
                                if (freeAllWeeks) {
                                    String oldRoom = c.getClassroom();
                                    c.setClassroom(altRoom.getRoomCode());
                                    schoolRepository.save(s);
                                    adjustNotes.add("Chuyển " + c.getCourseCode() + " từ phòng " + oldRoom + " sang " + altRoom.getRoomCode());
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<PeriodTimeDTO> default12Periods() {
        return List.of(
                new PeriodTimeDTO(1, LocalTime.of(7, 0), LocalTime.of(7, 50)),
                new PeriodTimeDTO(2, LocalTime.of(8, 0), LocalTime.of(8, 50)),
                new PeriodTimeDTO(3, LocalTime.of(9, 0), LocalTime.of(9, 50)),
                new PeriodTimeDTO(4, LocalTime.of(10, 0), LocalTime.of(10, 50)),
                new PeriodTimeDTO(5, LocalTime.of(11, 0), LocalTime.of(11, 50)),
                new PeriodTimeDTO(6, LocalTime.of(12, 0), LocalTime.of(13, 0)),
                new PeriodTimeDTO(7, LocalTime.of(13, 0), LocalTime.of(13, 50)),
                new PeriodTimeDTO(8, LocalTime.of(14, 0), LocalTime.of(14, 50)),
                new PeriodTimeDTO(9, LocalTime.of(15, 0), LocalTime.of(15, 50)),
                new PeriodTimeDTO(10, LocalTime.of(16, 0), LocalTime.of(16, 50)),
                new PeriodTimeDTO(11, LocalTime.of(17, 0), LocalTime.of(17, 50)),
                new PeriodTimeDTO(12, LocalTime.of(18, 0), LocalTime.of(18, 50))
        );
    }

    private LocalTime getPeriodStartTime(School school, int period) {
        if (school != null && school.getPeriods() != null) {
            Optional<LocalTime> time = school.getPeriods().stream()
                    .filter(p -> p != null && p.getPeriodNumber() == period)
                    .map(SchoolPeriod::getStartTime)
                    .findFirst();
            if (time.isPresent() && time.get() != null) {
                return time.get();
            }
        }
        throw new IllegalArgumentException("Thiếu giờ bắt đầu tiết " + period + ". Cần sửa bảng giờ của trường.");
    }

    private LocalTime getPeriodEndTime(School school, int period) {
        if (school != null && school.getPeriods() != null) {
            Optional<LocalTime> time = school.getPeriods().stream()
                    .filter(p -> p != null && p.getPeriodNumber() == period)
                    .map(SchoolPeriod::getEndTime)
                    .findFirst();
            if (time.isPresent() && time.get() != null) {
                return time.get();
            }
        }
        throw new IllegalArgumentException("Thiếu giờ kết thúc tiết " + period + ". Cần sửa bảng giờ của trường.");
    }

    private void detectConflicts(List<LessonSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                LessonSlot a = slots.get(i);
                LessonSlot b = slots.get(j);

                if (a.getDate().isEqual(b.getDate())) {
                    LocalTime maxStart = a.getStartTime().isAfter(b.getStartTime()) ? a.getStartTime() : b.getStartTime();
                    LocalTime minEnd = a.getEndTime().isBefore(b.getEndTime()) ? a.getEndTime() : b.getEndTime();

                    if (maxStart.isBefore(minEnd)) {
                        a.setConflict(true);
                        b.setConflict(true);
                        a.setConflictReason("Trùng ca dạy với " + b.getCourseName() + " (" + b.getSchoolName() + ")");
                        b.setConflictReason("Trùng ca dạy với " + a.getCourseName() + " (" + a.getSchoolName() + ")");
                    }
                }
            }
        }
    }
}