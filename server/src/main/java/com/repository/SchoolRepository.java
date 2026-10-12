package com.repository;

import com.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findByTeacher_TeacherIdAndSchoolCode(String teacherId, String schoolCode);
}