package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.CatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CatJpaRepository extends JpaRepository<CatEntity, Long> {

    @Query("""
            select c from CatEntity c
             where c.lastSeenLat between :minLat and :maxLat
               and c.lastSeenLng between :minLng and :maxLng
            """)
    List<CatEntity> findLastSeenInTerritory(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

    /**
     * Cats whose last observation is older than {@code cutoff} and have not yet received an absence ping.
     */
    List<CatEntity> findByLastSeenAtBeforeAndAbsenceReminderSentAtIsNull(Instant cutoff);

    /**
     * Atomically reserves the absence-reminder slot for a cat. Returns 1 if this invocation won the race.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CatEntity c
               set c.absenceReminderSentAt = :sentAt,
                   c.updatedAt = :sentAt
             where c.id = :id
               and c.absenceReminderSentAt is null
               and c.lastSeenAt < :cutoff
            """)
    int claimAbsenceReminderSlot(
            @Param("id") Long id,
            @Param("cutoff") Instant cutoff,
            @Param("sentAt") Instant sentAt
    );
}
