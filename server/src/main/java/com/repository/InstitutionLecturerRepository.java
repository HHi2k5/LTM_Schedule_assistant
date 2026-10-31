package com.repository;

import com.model.InstitutionLecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InstitutionLecturerRepository extends JpaRepository<InstitutionLecturer, Long> {
    boolean existsByInstitution_SchoolCodeAndLecturerCode(String schoolCode, String lecturerCode);
    List<InstitutionLecturer> findByInstitution_SchoolCodeOrderByLecturerCodeAsc(String schoolCode);
    Optional<InstitutionLecturer> findByIdAndInstitution_SchoolCode(Long id, String schoolCode);
}
