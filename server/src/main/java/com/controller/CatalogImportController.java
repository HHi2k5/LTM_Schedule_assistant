package com.controller;

import com.dto.CatalogImportDTO.*;
import com.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/institutions/{schoolCode}/imports/{kind}")
@RequiredArgsConstructor
public class CatalogImportController {
    private final CatalogImportService imports;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@PathVariable String schoolCode, @PathVariable String kind, @RequestParam("file") MultipartFile file) throws Exception {
        return imports.preview(schoolCode, kind(kind), file);
    }

    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommitResult commit(@PathVariable String schoolCode, @PathVariable String kind, @RequestParam("file") MultipartFile file,
                               @RequestParam("snapshot") String snapshot) throws Exception {
        return imports.commit(schoolCode, kind(kind), file, snapshot);
    }

    private static Kind kind(String value) {
        try { return Kind.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Loại danh mục cần lecturers, rooms hoặc assignments."); }
    }
}
