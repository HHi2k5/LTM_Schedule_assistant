package com.repository;

import com.model.SchoolPeriodTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolPeriodTemplateRepository extends JpaRepository<SchoolPeriodTemplate, Long> {
    List<SchoolPeriodTemplate> findBySchoolCodeOrderByPeriodNumberAsc(String schoolCode);
    long countBySchoolCode(String schoolCode);
    void deleteBySchoolCode(String schoolCode);
}
