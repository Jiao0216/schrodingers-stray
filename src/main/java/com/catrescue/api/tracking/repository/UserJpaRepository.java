package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.persistence.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    /**
     * Volunteers who registered alert coordinates within {@code radiusMeters} of the given point.
     */
    @Query(value = """
            SELECT u.* FROM users u
            WHERE u.role = 'VOLUNTEER'
              AND u.service_latitude IS NOT NULL
              AND u.service_longitude IS NOT NULL
              AND (
                6371000 * acos(
                  least(1.0, greatest(-1.0,
                    cos(radians(:lat)) * cos(radians(u.service_latitude))
                    * cos(radians(u.service_longitude) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(u.service_latitude))
                  ))
                )
              ) <= :radiusMeters
            """, nativeQuery = true)
    List<UserEntity> findVolunteersWithServicePointWithinMeters(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters
    );

    @Query("""
            select u from UserEntity u
             where u.role = :role
             order by u.volunteerPoints desc, u.id asc
            """)
    List<UserEntity> findVolunteerLeaderboard(@Param("role") UserRole role, Pageable pageable);
}
