package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.VolunteerNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VolunteerNotificationJpaRepository extends JpaRepository<VolunteerNotificationEntity, Long> {

    List<VolunteerNotificationEntity> findByVolunteerUserIdOrderByCreatedAtDesc(Long volunteerUserId);

    boolean existsByVolunteerUserIdAndRelatedSightingId(Long volunteerUserId, Long relatedSightingId);
}
