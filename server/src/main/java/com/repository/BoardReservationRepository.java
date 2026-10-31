package com.repository;

import com.model.TeachingCourse;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.*;

public interface BoardReservationRepository extends org.springframework.data.repository.Repository<TeachingCourse, Long> {
    // Canonical plan projections have dated entries. Legacy masks are expanded only
    // for this week in Java, using each teacher's own Monday anchor and actual clocks.
    @Query("""
        select c from TeachingCourse c join fetch c.school s join fetch s.teacher t
        left join SchedulingEntry e on e.id = c.schedulingEntryId
        where (c.schedulingPlanId is null or (c.schedulingPlanId <> :planId and e.lessonDate between :start and :end))
          and (upper(s.schoolCode) = :schoolCode or t.teacherId in :teacherIds)
        """)
    List<TeachingCourse> findRelevant(Long planId, String schoolCode, Collection<String> teacherIds, LocalDate start, LocalDate end);
}
