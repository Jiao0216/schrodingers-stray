package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.FeedingRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeedingRecordJpaRepository extends JpaRepository<FeedingRecordEntity, Long> {

    List<FeedingRecordEntity> findByCatIdOrderByFedAtDesc(Long catId);

    Optional<FeedingRecordEntity> findFirstByCatIdOrderByFedAtDesc(Long catId);

    /**
     * Cats with no feeding record having {@code fedAt >= since} (includes never fed).
     */
    @Query("""
            select c.id from CatEntity c
             where not exists (
               select 1 from FeedingRecordEntity fr
                where fr.catId = c.id and fr.fedAt >= :since
             )
            """)
    List<Long> findCatIdsWithoutFeedingSince(@Param("since") Instant since);
}
