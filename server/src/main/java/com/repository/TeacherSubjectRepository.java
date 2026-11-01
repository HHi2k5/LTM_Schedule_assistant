package com.repository;

import com.model.TeacherSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, Long> {
    List<TeacherSubject> findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc(String schoolCode);
    Optional<TeacherSubject> findBySchoolCodeAndTeacherIdIgnoreCaseAndCourseCodeIgnoreCaseAndCohortIgnoreCase(
            String schoolCode, String teacherId, String courseCode, String cohort);
}