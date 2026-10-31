package com.repository;

import com.model.SchedulingDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchedulingDemandRepository extends JpaRepository<SchedulingDemand, Long> {
    List<SchedulingDemand> findByVersion_IdOrderByDemandIndexAsc(Long versionId);
}
