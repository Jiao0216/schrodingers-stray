package com.catrescue.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AssessmentListItemDto(
        UUID id,
        String imageUrl,
        Double latitude,
        Double longitude,
        String addressText,
        String healthStatus,
        String neuteredStatus,
        Instant createdAt,
        CatFeaturesDto catFeatures,
        boolean imagePersisted,
        UUID identityClusterId,
        int clusterSize,
        UUID duplicateOfAssessmentId,
        boolean sameCatBadge,
        boolean needsHelpCategory,
        boolean healthyCategory,
        boolean tnrVerifiedCategory
) {
}
