package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.CatMatchFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatMatchFeedbackJpaRepository extends JpaRepository<CatMatchFeedbackEntity, Long> {

    Optional<CatMatchFeedbackEntity> findFirstBySightingIdOrderByIdDesc(Long sightingId);

    List<CatMatchFeedbackEntity> findByVolunteerConfirmedIsNotNullAndClipDistanceIsNotNullAndEmbeddingVersion(
            String embeddingVersion
    );
}
