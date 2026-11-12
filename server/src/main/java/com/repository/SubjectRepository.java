package com.repository;

import com.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findBySchoolCodeOrderByCourseCodeAsc(String schoolCode);
    List<Subject> findByDepartmentIdOrderByCourseCodeAsc(Long departmentId);
    Optional<Subject> findBySchoolCodeAndCourseCodeIgnoreCase(String schoolCode, String courseCode);
    long countByDepartmentId(Long departmentId);
}
