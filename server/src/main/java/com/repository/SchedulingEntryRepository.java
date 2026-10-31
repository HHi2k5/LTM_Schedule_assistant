package com.repository;

import com.model.SchedulingEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;

public interface SchedulingEntryRepository extends JpaRepository<SchedulingEntry, Long> {
    List<SchedulingEntry> findByVersion_IdOrderByLessonDateAscStartTimeAscIdAsc(Long versionId);
    Page<SchedulingEntry> findByVersion_Id(Long versionId, Pageable pageable);
    long countByVersion_Id(Long versionId);

    @Query("select e from SchedulingEntry e join fetch e.demand join fetch e.lecturer join fetch e.room where e.version.id = :versionId and e.lessonDate between :start and :end order by e.lessonDate, e.startTime, e.id")
    List<SchedulingEntry> findWeek(Long versionId, LocalDate start, LocalDate end);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"demand", "lecturer", "room"})
    @Query("select e from SchedulingEntry e where e.version.id=:versionId and (lower(e.courseName) like :q escape '!' or lower(e.classGroup) like :q escape '!' or lower(e.lecturerName) like :q escape '!' or lower(e.roomCode) like :q escape '!' or lower(e.demand.courseCode) like :q escape '!') order by e.lessonDate, e.startTime, e.id")
    Page<SchedulingEntry> searchEditable(Long versionId, String q, Pageable page);
}
