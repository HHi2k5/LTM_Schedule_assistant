package com.service;

import com.dto.AssignmentView.AssignRequest;
import com.dto.AssignmentView.AutoRequest;
import com.model.*;
import com.repository.*;
import com.security.AccountPrincipal;
import com.security.ScheduleAccess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherAssignmentServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final String MONDAY_A = "CSDL:2024:1:1:LT";
    private static final String MONDAY_B = "CSDL:2024:1:2:LT";
    private static final String TUESDAY = "CSDL:2024:1:3:LT";

    private ScheduleChangePublisher changes;
    private TeacherAssignmentService service;
    private Teacher pool, gv01, gv02, gv03, outsider;
    private School poolSchool;

    @BeforeEach
    void setUp() {
        var teachers = mock(TeacherRepository.class);
        var schools = mock(SchoolRepository.class);
        var subjects = mock(SubjectRepository.class);
        var departments = mock(DepartmentRepository.class);
        var teacherSubjects = mock(TeacherSubjectRepository.class);
        changes = mock(ScheduleChangePublisher.class);

        pool = teacher(Teacher.UNASSIGNED_ID, null, Teacher.ROLE_SYSTEM);
        poolSchool = school(pool, "PTIT");
        poolSchool.getCourses().addAll(List.of(lesson(poolSchool, MONDAY_A, 2, "101"), lesson(poolSchool, MONDAY_B, 2, "102"),
                lesson(poolSchool, TUESDAY, 3, "101")));
        gv01 = teacher("GV01", 1L, Teacher.ROLE_TEACHER);
        gv02 = teacher("GV02", 1L, Teacher.ROLE_TEACHER);
        gv03 = teacher("GV03", 1L, Teacher.ROLE_TEACHER);
        outsider = teacher("GV09", 2L, Teacher.ROLE_TEACHER);

        when(teachers.findAllWithFullSchedule()).thenReturn(List.of(pool, gv01, gv02, gv03, outsider));
        when(schools.save(any())).thenAnswer(call -> call.getArgument(0));
        when(subjects.findBySchoolCodeOrderByCourseCodeAsc("PTIT")).thenReturn(List.of(
                Subject.builder().id(1L).departmentId(1L).courseCode("CSDL").courseName("Cơ sở dữ liệu").build()));
        when(departments.findBySchoolCodeOrderByCodeAsc("PTIT")).thenReturn(List.of(
                Department.builder().id(1L).code("HTTT").name("Hệ thống thông tin").build(),
                Department.builder().id(2L).code("CNPM").name("Công nghệ phần mềm").build()));
        when(teacherSubjects.findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc("PTIT")).thenReturn(List.of(
                qualification("GV01", 2, TeacherSubject.KIND_PRIMARY), qualification("GV02", 1, TeacherSubject.KIND_PRIMARY),
                qualification("GV03", 1, TeacherSubject.KIND_SUBSTITUTE), qualification("GV09", 1, TeacherSubject.KIND_PRIMARY)));

        service = new TeacherAssignmentService(teachers, schools, subjects, departments, teacherSubjects,
                mock(ScheduleService.class), new ScheduleAccess(), mock(ScheduleWriteLock.class), changes);
        signIn("ADMIN", "ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assigningMovesEveryLessonOfTheClassToTheTeacherAndNotifiesBothSides() {
        service.assign(new AssignRequest("PTIT", MONDAY_A, "gv01"));

        assertEquals(Set.of(MONDAY_B, TUESDAY), classIds(poolSchool));
        var assigned = gv01.getSchools().getFirst();
        assertEquals("PTIT", assigned.getSchoolCode());
        assertEquals(Set.of(MONDAY_A), classIds(assigned));
        assertEquals(3, assigned.getPeriods().size());
        assertEquals("WEEKLY_AUTO", assigned.getCourses().iterator().next().getSourceType());
        verify(changes).changed(Teacher.UNASSIGNED_ID);
        verify(changes).changed("GV01");

        var view = service.overview("PTIT", null);
        assertEquals("GV01", view.classes().stream().filter(row -> row.classId().equals(MONDAY_A)).findFirst().orElseThrow().teacherId());
        assertEquals(1, view.teachers().stream().filter(load -> load.teacherId().equals("GV01")).findFirst().orElseThrow().classes());
    }

    @Test
    void aTeacherCannotTakeTwoClassesAtTheSameTime() {
        service.assign(new AssignRequest("PTIT", MONDAY_A, "GV01"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.assign(new AssignRequest("PTIT", MONDAY_B, "GV01")));
        assertTrue(error.getMessage().contains("Trùng giờ"));
        assertEquals(Set.of(MONDAY_B, TUESDAY), classIds(poolSchool));

        var row = service.overview("PTIT", 1L).classes().stream().filter(item -> item.classId().equals(MONDAY_B)).findFirst().orElseThrow();
        assertNotNull(row.candidates().stream().filter(c -> c.teacherId().equals("GV01")).findFirst().orElseThrow().blockedReason());
        assertNull(row.candidates().stream().filter(c -> c.teacherId().equals("GV02")).findFirst().orElseThrow().blockedReason());
    }

    @Test
    void aPersonalLessonAtAnotherSchoolAlsoBlocksTheAssignment() {
        var hust = school(gv02, "HUST");
        hust.getCourses().add(TeachingCourse.builder().courseCode("X").courseName("Lịch cá nhân").classroom("B1")
                .dayOfWeek(2).startPeriod(2).numPeriods(1).weekSchedule("1").sourceType("EXCEL_UPLOAD").school(hust).build());
        assertThrows(IllegalArgumentException.class, () -> service.assign(new AssignRequest("PTIT", MONDAY_A, "GV02")));
        service.assign(new AssignRequest("PTIT", TUESDAY, "GV02"));
        assertEquals(Set.of(TUESDAY), classIds(gv02.getSchools().stream().filter(s -> s.getSchoolCode().equals("PTIT")).findFirst().orElseThrow()));
    }

    @Test
    void onlyTeachersOfTheSubjectsDepartmentCanBeAssigned() {
        assertThrows(IllegalArgumentException.class, () -> service.assign(new AssignRequest("PTIT", MONDAY_A, "GV09")));
        assertThrows(IllegalArgumentException.class, () -> service.assign(new AssignRequest("PTIT", MONDAY_A, "UNKNOWN")));
    }

    @Test
    void aDepartmentHeadCanOnlyAssignWithinTheirOwnDepartment() {
        signIn("GV09", "DEPT_HEAD", 2L);
        assertThrows(AccessDeniedException.class, () -> service.assign(new AssignRequest("PTIT", MONDAY_A, "GV01")));
        // Yêu cầu bộ môn khác bị quy về bộ môn của chính trưởng bộ môn: không lớp nào của bộ môn 1 bị đụng tới.
        assertEquals(0, service.autoAssign(new AutoRequest("PTIT", 1L)).assigned());
        assertEquals(3, poolSchool.getCourses().size());
        assertTrue(service.overview("PTIT", 1L).classes().isEmpty());

        signIn("GV01", "DEPT_HEAD", 1L);
        service.assign(new AssignRequest("PTIT", MONDAY_A, "GV02"));
        assertEquals(3, service.overview("PTIT", null).classes().size());
    }

    @Test
    void unassigningReturnsTheClassToThePool() {
        service.assign(new AssignRequest("PTIT", MONDAY_A, "GV01"));
        service.assign(new AssignRequest("PTIT", MONDAY_A, null));
        assertEquals(Set.of(MONDAY_A, MONDAY_B, TUESDAY), classIds(poolSchool));
        assertTrue(gv01.getSchools().isEmpty());
    }

    @Test
    void autoAssignFollowsWeightsPrefersPrimaryTeachersAndNeverDoubleBooks() {
        var result = service.autoAssign(new AutoRequest("PTIT", 1L));
        assertEquals(3, result.assigned());
        assertEquals("SUCCESS", result.status());
        // Trọng số 2:1 -> GV01 nhận 2 lớp, GV02 nhận 1; hai lớp cùng giờ sáng thứ Hai phải ở hai giảng viên khác nhau.
        assertEquals(Set.of(MONDAY_A, TUESDAY), classIds(gv01.getSchools().getFirst()));
        assertEquals(Set.of(MONDAY_B), classIds(gv02.getSchools().getFirst()));
        assertTrue(gv03.getSchools().isEmpty());
        assertTrue(poolSchool.getCourses().isEmpty());
    }

    @Test
    void autoAssignUsesASubstituteOnlyWhenNoPrimaryTeacherIsFree() {
        poolSchool.getCourses().add(lesson(poolSchool, "CSDL:2024:1:4:LT", 2, "103"));
        var result = service.autoAssign(new AutoRequest("PTIT", 1L));
        assertEquals(4, result.assigned());
        // Ba lớp cùng giờ thứ Hai nhưng chỉ có hai giảng viên phụ trách -> lớp thứ ba về giảng viên dạy thay.
        assertEquals(1, gv03.getSchools().getFirst().getCourses().size());
    }

    private static void signIn(String id, String role, Long department) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AccountPrincipal(id, id, role, department), null, List.of()));
    }

    private static Set<String> classIds(School school) {
        var ids = new TreeSet<String>();
        school.getCourses().forEach(course -> ids.add(course.getScheduleClassId()));
        return ids;
    }

    private static Teacher teacher(String id, Long department, String role) {
        return Teacher.builder().teacherId(id).fullName("Teacher " + id).username(id).role(role).departmentId(department)
                .semesterStartDate(START).schools(new ArrayList<>()).build();
    }

    private static School school(Teacher teacher, String code) {
        var school = School.builder().schoolCode(code).schoolName(code).teacher(teacher)
                .periods(new HashSet<>()).courses(new HashSet<>()).build();
        for (int period = 1; period <= 3; period++) {
            school.getPeriods().add(SchoolPeriod.builder().periodNumber(period).startTime(LocalTime.of(6 + period, 0))
                    .endTime(LocalTime.of(6 + period, 50)).school(school).build());
        }
        teacher.getSchools().add(school);
        return school;
    }

    private static TeachingCourse lesson(School school, String classId, int day, String room) {
        return TeachingCourse.builder().courseCode("CSDL").courseName("[LT] Cơ sở dữ liệu (" + classId + ")").classroom(room)
                .dayOfWeek(day).startPeriod(1).numPeriods(3).weekSchedule("1").sourceType("WEEKLY_AUTO")
                .scheduleClassId(classId).sessionType("THEORY").school(school).build();
    }

    private static TeacherSubject qualification(String teacherId, int weight, String kind) {
        return TeacherSubject.builder().schoolCode("PTIT").teacherId(teacherId).teacherName("Teacher " + teacherId)
                .courseCode("CSDL").courseName("Cơ sở dữ liệu").theoryWeight(weight).practiceWeight(weight)
                .assignmentKind(kind).build();
    }
}
