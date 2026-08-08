package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.FeedingSpotRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedingSpotRecordJpaRepository extends JpaRepository<FeedingSpotRecordEntity, Long> {

    List<FeedingSpotRecordEntity> findByFeedingSpotIdOrderByFedAtDesc(Long feedingSpotId);
}
