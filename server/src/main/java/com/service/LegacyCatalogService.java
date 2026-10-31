package com.service;

import com.dto.CatalogImportDTO.*;
import com.repository.RoomRepository;
import com.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read-only suggestions; users choose the school name and review the ordinary import. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegacyCatalogService {
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final CatalogService catalogs;

    private static final class Candidate {
        final Set<String> names = new TreeSet<>();
        final Map<String, LegacyLecturer> lecturers = new TreeMap<>();
        long roomCount;
    }

    public List<LegacySchool> preview() {
        var candidates = new TreeMap<String, Candidate>();
        rooms.findAllByOrderByCapacityAsc().forEach(room -> {
            if (room.getSchoolCode() != null && !room.getSchoolCode().isBlank())
                candidates.computeIfAbsent(key(room.getSchoolCode()), ignored -> new Candidate()).roomCount++;
        });
        for (var teacher : teachers.findAllWithFullSchedule()) for (var school : teacher.getSchools()) {
            if (school.getSchoolCode() == null || school.getSchoolCode().isBlank()) continue;
            var candidate = candidates.computeIfAbsent(key(school.getSchoolCode()), ignored -> new Candidate());
            if (school.getSchoolName() != null && !school.getSchoolName().isBlank()) candidate.names.add(school.getSchoolName().strip());
            if (!"TEACHER".equals(teacher.getRole())) continue;
            String accountId = teacher.getUsername() == null || teacher.getUsername().isBlank() ? null : teacher.getTeacherId();
            candidate.lecturers.putIfAbsent(teacher.getTeacherId(), new LegacyLecturer(teacher.getTeacherId(),
                    teacher.getFullName() == null || teacher.getFullName().isBlank() ? teacher.getTeacherId() : teacher.getFullName(), accountId,
                    accountId == null ? teacher.getTeacherId() : null));
        }
        var registered = new HashSet<String>();
        catalogs.listInstitutions().forEach(s -> registered.add(s.schoolCode()));
        return candidates.entrySet().stream().map(entry -> {
            String code = entry.getKey(); var candidate = entry.getValue();
            boolean supported;
            try { CatalogService.schoolCode(code); supported = true; } catch (IllegalArgumentException e) { supported = false; }
            return new LegacySchool(code, List.copyOf(candidate.names), candidate.roomCount, registered.contains(code), supported,
                    List.copyOf(candidate.lecturers.values()));
        }).toList();
    }

    private static String key(String code) { return code.strip().toUpperCase(Locale.ROOT); }
}
