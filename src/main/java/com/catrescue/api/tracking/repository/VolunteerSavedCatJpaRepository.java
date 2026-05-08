package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.VolunteerSavedCatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VolunteerSavedCatJpaRepository extends JpaRepository<VolunteerSavedCatEntity, Long> {

    Optional<VolunteerSavedCatEntity> findByUserIdAndCatId(long userId, long catId);

    List<VolunteerSavedCatEntity> findByUserIdOrderBySavedAtDesc(long userId);

    void deleteByUserIdAndCatId(long userId, long catId);
}
