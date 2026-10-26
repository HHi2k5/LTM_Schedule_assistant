package com.service;

import com.dto.*;
import com.repository.TeacherRepository;
import com.repository.RoomRepository;
import com.model.Room;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AutoScheduleServiceTest {
    @Test void generatedSessionsKeepOriginalNameCreditsAndExportContext() {
        var teachers = mock(TeacherRepository.class);
        when(teachers.findTeacherWithFullSchedule(anyString())).thenReturn(Optional.empty());
        var rooms = mock(RoomRepository.class);
        var theory = new Room(); theory.setRoomCode("101-A2"); theory.setCapacity(60); theory.setLab(false);
        var lab = new Room(); lab.setRoomCode("LAB-A2"); lab.setCapacity(30); lab.setLab(true);
        when(rooms.findAllByOrderByCapacityAsc()).thenReturn(List.of(theory, lab));
        var service = new AutoScheduleService(teachers, rooms, mock(ScheduleService.class));
        List<PeriodTimeDTO> periods = new ArrayList<>();
        for (int p = 1; p <= 10; p++) periods.add(new PeriodTimeDTO(p, LocalTime.of(6 + p, 0), LocalTime.of(6 + p, 50)));
        var request = SchoolWideScheduleRequest.builder().semesterStartDate(LocalDate.of(2026, 8, 10))
                .totalWeeks(16).buildingSuffix("A2")
                .targetSchool(SchoolConfigDTO.builder().schoolId("PTIT").schoolName("School").periods(periods).build())
                .courses(List.of(CourseDemandDTO.builder().courseCode("INT1313").courseName("Networking").credits(3)
                        .totalClassesToOpen(1).teachers(List.of(TeacherCandidateDTO.builder().teacherId("GV01")
                                .teacherName("Teacher").assignedClasses(1).build())).build())).build();
        var result = service.generateSchoolWideSchedule(request);
        assertEquals("PTIT", result.getSchoolId()); assertEquals(request.getSemesterStartDate(), result.getSemesterStartDate());
        assertEquals(periods, result.getPeriods()); assertFalse(result.getScheduledClasses().isEmpty());
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> "Networking".equals(slot.getBaseCourseName()) && slot.getCredits() == 3));
        assertEquals(Set.of("THEORY", "PRACTICE"), new HashSet<>(result.getScheduledClasses().stream().map(GeneratedClassSlot::getSessionType).toList()));
        assertTrue(result.getScheduledClasses().stream().allMatch(slot -> slot.getNumPeriods() == 3));
    }
}
