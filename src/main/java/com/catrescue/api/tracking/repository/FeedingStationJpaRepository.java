package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedingStationJpaRepository extends JpaRepository<FeedingStationEntity, Long> {

    List<FeedingStationEntity> findByManagedByOrganizationIdOrderByIdAsc(Long organizationId);

    @Query("""
            select f from FeedingStationEntity f
             where f.latitude between :minLat and :maxLat
               and f.longitude between :minLng and :maxLng
            order by f.id asc
            """)
    List<FeedingStationEntity> findInTerritory(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );
}
