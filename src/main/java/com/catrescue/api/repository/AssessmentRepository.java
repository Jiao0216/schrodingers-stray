package com.catrescue.api.repository;

import com.catrescue.api.domain.Assessment;
import com.catrescue.api.dto.StoredAssessmentImage;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository {

    /** Persists {@code optionalStoredImageBytes} when non-empty (subject to service-level persist toggle). */
    Assessment save(Assessment assessment, byte[] optionalStoredImageBytes);

    Optional<Assessment> findById(UUID id);

    Optional<StoredAssessmentImage> findStoredImage(UUID id);

    List<Assessment> findTop500ByCreatedAtDesc();

    /** All assessments, newest first. */
    List<Assessment> findAllByOrderByCreatedAtDesc();

    /**
     * All assessments ordered by {@code createdAt} descending (same as {@link #findAllByOrderByCreatedAtDesc()}).
     */
    List<Assessment> findAll();

    /** All assessments with the given sort (typically {@code createdAt} descending). */
    List<Assessment> findAll(Sort sort);

    List<Assessment> findByIdentityClusterIdOrdered(UUID identityClusterId);

    long countByIdentityClusterId(UUID identityClusterId);
}
