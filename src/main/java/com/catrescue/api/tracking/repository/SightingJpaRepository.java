package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.domain.DedupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SightingJpaRepository extends JpaRepository<SightingEntity, Long> {

    @Query(value = """
            SELECT s.* FROM sightings s
            WHERE s.occurred_at >= :since
              AND (
                6371000 * acos(
                  cos(radians(:lat)) * cos(radians(s.latitude))
                  * cos(radians(s.longitude) - radians(:lng))
                  + sin(radians(:lat)) * sin(radians(s.latitude))
                )
              ) <= :radiusMeters
            ORDER BY s.occurred_at DESC
            """, nativeQuery = true)
    List<SightingEntity> findCandidatesWithinRadiusAndTime(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("since") Instant since,
            @Param("radiusMeters") double radiusMeters
    );

    Page<SightingEntity> findByDedupStatusOrderByOccurredAtDesc(DedupStatus status, Pageable pageable);

    Page<SightingEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Optional<SightingEntity> findById(Long id);

    /** All sightings for a cat since {@code occurredAtMin} (inclusive), any dedup status with cat assigned. */
    List<SightingEntity> findByCatIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long catId,
            Instant occurredAtMin
    );

    /** All sightings since {@code occurredAtMin} (inclusive), ordered by newest first. */
    List<SightingEntity> findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(Instant occurredAtMin);

    /** Latest sighting for determining who last observed the cat. */
    Optional<SightingEntity> findFirstByCatIdOrderByOccurredAtDesc(Long catId);

    @Query("select distinct s.catId from SightingEntity s where s.catId is not null")
    List<Long> findDistinctCatIds();

    long countByOccurredAtGreaterThanEqual(Instant since);

    @Query("select count(distinct s.catId) from SightingEntity s where s.catId is not null")
    long countDistinctCatIdWithNonNullCat();

    Page<SightingEntity> findByReporterUserIdOrderByOccurredAtDesc(Long reporterUserId, Pageable pageable);

    long countByReporterUserId(Long reporterUserId);

    boolean existsByCatIdAndReporterUserId(Long catId, Long reporterUserId);
}
