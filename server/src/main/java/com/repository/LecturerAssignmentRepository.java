package com.repository;

import com.model.LecturerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LecturerAssignmentRepository extends JpaRepository<LecturerAssignment, Long> {
    List<LecturerAssignment> findByLecturer_IdOrderByCourseCodeAscCohortAsc(Long lecturerId);
    Optional<LecturerAssignment> findByIdAndLecturer_Id(Long id, Long lecturerId);
    List<LecturerAssignment> findByLecturer_Institution_SchoolCodeOrderByIdAsc(String schoolCode);
}
