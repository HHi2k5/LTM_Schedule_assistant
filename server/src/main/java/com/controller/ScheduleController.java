package com.controller;

import com.dto.SchoolUploadRequest;
import com.dto.WeekScheduleResponse;
import com.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/upload-school")
    public ResponseEntity<?> uploadSchoolSchedule(@RequestBody SchoolUploadRequest request) {
        scheduleService.saveOrUpdateSchoolSchedule(request);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã lưu và gộp lịch của trường " + request.getSchool().getSchoolName() + " thành công!"
        ));
    }

    @GetMapping("/week/{weekIndex}")
    public ResponseEntity<WeekScheduleResponse> getWeekSchedule(
            @PathVariable int weekIndex,
            @RequestParam(defaultValue = "GV01") String teacherId) {
        return ResponseEntity.ok(scheduleService.getWeeklySchedule(teacherId, weekIndex));
    }
}