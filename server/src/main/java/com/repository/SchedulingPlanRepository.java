package com.repository;

import com.model.SchedulingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchedulingPlanRepository extends JpaRepository<SchedulingPlan, Long> {
    List<SchedulingPlan> findAllByOrderByUpdatedAtDescIdDesc();
    boolean existsByInstitution_SchoolCodeAndConfirmedVersionIsNotNull(String schoolCode);
}
