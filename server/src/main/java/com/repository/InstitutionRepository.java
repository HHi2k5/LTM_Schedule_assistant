package com.repository;

import com.model.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InstitutionRepository extends JpaRepository<Institution, String> {
    List<Institution> findAllByOrderBySchoolCodeAsc();
}
