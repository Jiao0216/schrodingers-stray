package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.domain.VolunteerBadgeCode;
import com.catrescue.api.tracking.persistence.VolunteerBadgeEarnedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VolunteerBadgeEarnedJpaRepository extends JpaRepository<VolunteerBadgeEarnedEntity, Long> {

    List<VolunteerBadgeEarnedEntity> findByUserIdOrderByEarnedAtAsc(Long userId);

    boolean existsByUserIdAndBadgeCode(Long userId, VolunteerBadgeCode badgeCode);
}
