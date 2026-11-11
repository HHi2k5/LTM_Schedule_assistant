package com.repository;

import com.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findBySchoolCodeOrderByCodeAsc(String schoolCode);
    Optional<Department> findBySchoolCodeAndCodeIgnoreCase(String schoolCode, String code);
}
