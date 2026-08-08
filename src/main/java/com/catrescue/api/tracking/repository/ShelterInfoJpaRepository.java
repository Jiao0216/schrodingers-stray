package com.catrescue.api.tracking.repository;

import com.catrescue.api.tracking.persistence.ShelterInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShelterInfoJpaRepository extends JpaRepository<ShelterInfoEntity, Long> {
}
