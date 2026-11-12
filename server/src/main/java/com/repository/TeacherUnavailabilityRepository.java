package com.repository;

import com.model.TeacherUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherUnavailabilityRepository extends JpaRepository<TeacherUnavailability, Long> {
    List<TeacherUnavailability> findBySchoolCode(String schoolCode);
    List<TeacherUnavailability> findBySchoolCodeAndTeacherIdIgnoreCaseOrderByDayOfWeekAscSpecificDateAsc(String schoolCode, String teacherId);
    void deleteBySchoolCodeAndTeacherIdIgnoreCaseAndSpecificDateIsNull(String schoolCode, String teacherId);
}
