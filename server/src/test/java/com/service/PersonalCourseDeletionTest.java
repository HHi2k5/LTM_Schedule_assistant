package com.service;

import com.model.School;
import com.model.Teacher;
import com.model.TeachingCourse;
import com.repository.*;
import com.security.ScheduleAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalCourseDeletionTest {
    private TeacherRepository teachers;
    private SchoolRepository schools;
    private ScheduleAccess access;
    private ScheduleChangePublisher changes;
    private ScheduleService service;
    private Teacher teacher;
    private School ptit;
    private School other;

    @BeforeEach
    void setUp() {
        teachers = mock(TeacherRepository.class);
        schools = mock(SchoolRepository.class);
        access = mock(ScheduleAccess.class);
        changes = mock(ScheduleChangePublisher.class);
        service = new ScheduleService(teachers, schools, mock(RoomRepository.class), mock(TeacherSubjectRepository.class),
                mock(PasswordEncoder.class), access, mock(ScheduleWriteLock.class), changes);

        teacher = Teacher.builder().teacherId("GV01").semesterStartDate(LocalDate.of(2026, 8, 10)).schools(new ArrayList<>()).build();
        ptit = school("PTIT", course(1L, "EXCEL_UPLOAD"), course(2L, "WEEKLY_AUTO"), course(3L, "VISITING_AUTO"));
        other = school("HUST", course(4L, "EXCEL_UPLOAD"));
        teacher.getSchools().addAll(List.of(ptit, other));
        when(teachers.findTeacherWithFullSchedule("GV01")).thenReturn(Optional.of(teacher));
    }

    @Test
    void deletesOnlyPersonalCoursesAndKeepsDepartmentAssignedOnes() {
        int removed = service.deletePersonalCourses("GV01", List.of(1L, 2L, 3L));

        assertEquals(1, removed);
        assertEquals(List.of(2L, 3L), ptit.getCourses().stream().map(TeachingCourse::getId).sorted().toList());
        assertTrue(teacher.getSchools().contains(ptit));
        verify(schools, never()).delete(ptit);
        verify(changes).changed("GV01");
    }

    @Test
    void removesTheSchoolWhenItsLastPersonalCourseIsDeleted() {
        assertEquals(1, service.deletePersonalCourses("GV01", List.of(4L)));
        assertFalse(teacher.getSchools().contains(other));
        verify(schools).delete(other);
    }

    @Test
    void rejectsRequestsThatContainOnlyDepartmentCourses() {
        assertThrows(IllegalArgumentException.class, () -> service.deletePersonalCourses("GV01", List.of(2L, 3L)));
        assertEquals(3, ptit.getCourses().size());
        verify(changes, never()).changed(anyString());
    }

    @Test
    void listMarksDepartmentAssignedCourses() {
        var list = service.listTeacherCourses("GV01");
        assertEquals(4, list.size());
        assertEquals(List.of(1L, 4L), list.stream().filter(c -> !c.isDepartmentAssigned()).map(c -> c.getId()).sorted().toList());
    }

    @Test
    void teacherDeletingAWholeSchoolKeepsDepartmentCourses() {
        when(access.isAdmin()).thenReturn(false);
        when(schools.findByTeacher_TeacherIdAndSchoolCode("GV01", "PTIT")).thenReturn(Optional.of(ptit));
        service.deleteSchoolSchedule("GV01", "PTIT");
        assertEquals(List.of(2L, 3L), ptit.getCourses().stream().map(TeachingCourse::getId).sorted().toList());
        verify(schools, never()).delete(ptit);
    }

    @Test
    void personalViewUsesTheColorChosenAtImportButDepartmentScheduleKeepsTeacherColor() {
        other.setColorCode("#dc2626");
        ptit.setColorCode("#dc2626");
        for (School school : List.of(ptit, other)) school.getCourses().forEach(c -> {
            c.setCustomStartTime(java.time.LocalTime.of(7, 0));
            c.setCustomEndTime(java.time.LocalTime.of(8, 40));
        });
        var week = service.getWeeklySchedule("GV01", 1);
        week.getLessons().forEach(lesson -> assertEquals(
                "HUST".equals(lesson.getSchoolId()) ? "#dc2626" : "#2563eb", lesson.getColorCode(), lesson.getSchoolId()));
        assertTrue(week.getLessons().stream().anyMatch(l -> "HUST".equals(l.getSchoolId())));
    }

    private School school(String code, TeachingCourse... courses) {
        School school = School.builder().schoolCode(code).schoolName(code).teacher(teacher).courses(new HashSet<>()).build();
        for (TeachingCourse c : courses) { c.setSchool(school); school.getCourses().add(c); }
        return school;
    }

    private static TeachingCourse course(long id, String source) {
        return TeachingCourse.builder().id(id).courseName("Course " + id).sourceType(source)
                .dayOfWeek(2).startPeriod(1).numPeriods(2).weekSchedule("1111").build();
    }
}
