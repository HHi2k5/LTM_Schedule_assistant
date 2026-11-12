package com.repository;

import com.model.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findBySchoolCodeOrderByUsageKindDescNameAsc(String schoolCode);
    Optional<RoomType> findBySchoolCodeAndCodeIgnoreCase(String schoolCode, String code);
    long countBySchoolCode(String schoolCode);
}
