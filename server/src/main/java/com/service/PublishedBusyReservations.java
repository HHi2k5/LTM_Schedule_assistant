package com.service;
import com.dto.*;
import com.repository.LecturerBusySlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PublishedBusyReservations {
    private final LecturerBusySlotRepository slots;
    List<WeeklyReservations.Reservation> reservations(Long excludedPlan, LocalDate start, LocalDate end) {
        return slots.findPublished(excludedPlan, start, end).stream().map(b -> busy(b.getScheduleTeacher().getTeacherId(), b.getBusyDate(), b.getStartTime(), b.getEndTime())).toList();
    }
    List<WeeklyReservations.Reservation> forRequest(SchoolWideScheduleRequest request) {
        if (request == null || request.getSemesterStartDate() == null) return List.of();
        var result = new ArrayList<>(reservations(request.getSchedulingPlanId(), request.getSemesterStartDate(), request.getSemesterStartDate().plusWeeks(100).minusDays(1)));
        var identities = new HashMap<Long, String>();
        if (request.getCourses() != null) request.getCourses().forEach(c -> { if (c != null && c.getTeachers() != null) c.getTeachers().forEach(t -> identities.put(t.getCatalogLecturerId(), t.getTeacherId())); });
        for (var row : own(request)) if (identities.containsKey(row.lecturerId())) result.add(busy(identities.get(row.lecturerId()), row.date(), row.startTime(), row.endTime()));
        return result;
    }
    static List<PlanBusyDTO> own(SchoolWideScheduleRequest request) { return request.getBusySlots() == null ? List.of() : request.getBusySlots(); }
    static WeeklyReservations.Reservation busy(String teacherId, LocalDate date, LocalTime start, LocalTime end) {
        return new WeeklyReservations.Reservation(teacherId, "", null, date, start, end, null, null, false, false, true);
    }
}
