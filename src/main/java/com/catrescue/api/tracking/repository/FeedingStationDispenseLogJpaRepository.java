package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.FeedingStationDispenseLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FeedingStationDispenseLogJpaRepository extends JpaRepository<FeedingStationDispenseLogEntity, Long> {

    @Query("select count(l) from FeedingStationDispenseLogEntity l where l.scheduled = true and l.dispensedAt >= :since")
    long countScheduledSince(@Param("since") Instant since);
}
