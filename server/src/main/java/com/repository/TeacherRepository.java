package com.repository;

import com.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface TeacherRepository extends JpaRepository<Teacher, String> {
    
    // Fetch the list of schools separately from its two sets. Joining all three
    // collections duplicates schools once per period/course combination.
    @Query("SELECT DISTINCT t FROM Teacher t LEFT JOIN FETCH t.schools WHERE t.teacherId = :teacherId")
    Optional<Teacher> findTeacherWithSchools(@Param("teacherId") String teacherId);

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    default Optional<Teacher> findTeacherWithFullSchedule(String teacherId) {
        var result = findTeacherWithSchools(teacherId);
        result.ifPresent(TeacherRepository::initializeSchedule);
        return result;
    }
    Optional<Teacher> findByUsername(String username);
    Optional<Teacher> findByUsernameIgnoreCase(String username);
    Optional<Teacher> findByTeacherIdIgnoreCase(String teacherId);
    boolean existsByUsername(String username);

    @Query("SELECT DISTINCT t FROM Teacher t LEFT JOIN FETCH t.schools")
    List<Teacher> findAllWithSchools();

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    default List<Teacher> findAllWithFullSchedule() {
        var result = findAllWithSchools();
        result.forEach(TeacherRepository::initializeSchedule);
        return result;
    }

    private static void initializeSchedule(Teacher teacher) {
        teacher.getSchools().forEach(school -> { school.getPeriods().size(); school.getCourses().size(); });
    }
}
