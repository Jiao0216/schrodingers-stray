package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.VolunteerFeedingCheckInEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerFeedingCheckInJpaRepository extends JpaRepository<VolunteerFeedingCheckInEntity, Long> {

    long countByUserId(Long userId);
}
