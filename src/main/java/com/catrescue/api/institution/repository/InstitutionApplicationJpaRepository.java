package com.catrescue.api.institution.repository;

import com.catrescue.api.institution.domain.InstitutionApplicationStatus;
import com.catrescue.api.institution.persistence.InstitutionApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstitutionApplicationJpaRepository extends JpaRepository<InstitutionApplicationEntity, Long> {

    List<InstitutionApplicationEntity> findByStatusOrderByCreatedAtDesc(InstitutionApplicationStatus status);
}
