package com.catrescue.api.institution.repository;

import com.catrescue.api.institution.persistence.InstitutionVolunteerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstitutionVolunteerJpaRepository extends JpaRepository<InstitutionVolunteerEntity, Long> {

    List<InstitutionVolunteerEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    boolean existsByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);
}
