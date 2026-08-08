package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.FeedingSpotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedingSpotJpaRepository extends JpaRepository<FeedingSpotEntity, Long> {

    List<FeedingSpotEntity> findByActiveTrueOrderByCreatedAtDesc();
}
