package com.repository;

import com.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, String> {
    
    // Nạp kèm toàn bộ trường, tiết và môn học để tránh lỗi N+1
    @Query("SELECT DISTINCT t FROM Teacher t " +
           "LEFT JOIN FETCH t.schools s " +
           "LEFT JOIN FETCH s.periods " +
           "LEFT JOIN FETCH s.courses " +
           "WHERE t.teacherId = :teacherId")
    Optional<Teacher> findTeacherWithFullSchedule(@Param("teacherId") String teacherId);
    Optional<Teacher> findByUsername(String username);
    boolean existsByUsername(String username);
}