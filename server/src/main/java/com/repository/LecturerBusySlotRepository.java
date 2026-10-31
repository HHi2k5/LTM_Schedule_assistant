package com.repository;
import com.model.LecturerBusySlot;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.List;

public interface LecturerBusySlotRepository extends JpaRepository<LecturerBusySlot, Long> {
    List<LecturerBusySlot> findByVersion_Id(Long versionId);
    @Query("select b from LecturerBusySlot b join SchedulingPlan p on p.confirmedVersion = b.version join fetch b.scheduleTeacher where (:excludedPlan is null or p.id <> :excludedPlan) and b.busyDate between :start and :end")
    List<LecturerBusySlot> findPublished(Long excludedPlan, LocalDate start, LocalDate end);
    @Query("select count(b) from LecturerBusySlot b join SchedulingPlan p on p.confirmedVersion = b.version where b.scheduleTeacher.teacherId = :teacherId")
    long countActiveForTeacher(String teacherId);
}
