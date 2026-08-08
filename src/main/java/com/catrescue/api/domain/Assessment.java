package com.catrescue.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Optional stored upload binary is kept only in {@link com.catrescue.api.persistence.AssessmentEntity};
 * {@code imageStored} reflects whether bytes were saved when persist-images is enabled.
 */
public record Assessment(
        UUID id,
        AssessmentStatus status,
        String originalFilename,
        String contentType,
        Double latitude,
        Double longitude,
        BranchType branchType,
        ModelLabels modelLabels,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        boolean imageStored,
        String addressText,
        CatFeatureSnapshot catFeatures,
        UUID duplicateOfAssessmentId,
        Double duplicateSimilarityScore,
        UUID identityClusterId,
        /** User form: yes | no | unknown — overrides model neuter label in community UI. */
        String userEarClip,
        /** Sighting row created from this assessment upload (for CLIP ↔ assessment linking). */
        Long trackingSightingId
) {
}
