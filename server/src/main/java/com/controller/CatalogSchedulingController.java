package com.controller;

import com.dto.AutoScheduleResponse;
import com.dto.CatalogSchedulingDTO.*;
import com.service.CatalogSchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class CatalogSchedulingController {
    private final CatalogSchedulingService schedules;
    private final ObjectMapper json;

    @GetMapping("/api/admin/institutions/{schoolCode}/scheduling-options")
    public Options options(@PathVariable String schoolCode) throws Exception { return schedules.options(schoolCode); }

    @PostMapping(value = "/api/schedule/auto-generate-catalog", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AutoScheduleResponse generate(@RequestParam String schoolId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semesterStartDate,
            @RequestParam MultipartFile planFile, @RequestParam String configuration) throws Exception {
        Configuration parsed;
        try { parsed = json.readValue(configuration, Configuration.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Cấu hình danh mục không phải JSON hợp lệ."); }
        return schedules.generate(schoolId, semesterStartDate, planFile, parsed);
    }
}
