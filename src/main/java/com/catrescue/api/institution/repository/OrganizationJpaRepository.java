package com.catrescue.api.institution.repository;

import com.catrescue.api.institution.persistence.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, Long> {

    Optional<OrganizationEntity> findByApiPublicId(String apiPublicId);
}
