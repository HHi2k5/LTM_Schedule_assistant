package com.repository;

import com.model.SchedulingPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SchedulingPlanVersionRepository extends JpaRepository<SchedulingPlanVersion, Long> {
    List<SchedulingPlanVersion> findByPlan_IdOrderByVersionNumberDesc(Long planId);
    Optional<SchedulingPlanVersion> findByIdAndPlan_Id(Long id, Long planId);
}
