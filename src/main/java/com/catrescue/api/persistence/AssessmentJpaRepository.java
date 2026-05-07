package com.catrescue.api.persistence;

import com.catrescue.api.domain.AssessmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AssessmentJpaRepository extends JpaRepository<AssessmentEntity, UUID> {

    List<AssessmentEntity> findAllByOrderByCreatedAtDesc();

    List<AssessmentEntity> findTop400ByStatusOrderByCreatedAtDesc(AssessmentStatus status);

    List<AssessmentEntity> findTop500ByOrderByCreatedAtDesc();

    List<AssessmentEntity> findByIdentityClusterIdOrderByCreatedAtAsc(UUID identityClusterId);

    long countByIdentityClusterId(UUID identityClusterId);

    @Query("select count(a) from AssessmentEntity a where a.latitude is not null and a.longitude is not null")
    long countWithLatitudeAndLongitudeNotNull();
}
