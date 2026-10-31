package com.repository;

import com.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByInstitution_SchoolCodeOrderByDepartmentCodeAsc(String schoolCode);
    Optional<Department> findByIdAndInstitution_SchoolCode(Long id, String schoolCode);
}
