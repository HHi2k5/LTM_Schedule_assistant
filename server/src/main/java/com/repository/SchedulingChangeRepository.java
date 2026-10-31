package com.repository;
import com.model.SchedulingChange;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface SchedulingChangeRepository extends JpaRepository<SchedulingChange, Long> {
    @EntityGraph(attributePaths = {"sourceVersion", "resultVersion"})
    Page<SchedulingChange> findByPlan_IdOrderByIdDesc(Long planId, Pageable page);
}
