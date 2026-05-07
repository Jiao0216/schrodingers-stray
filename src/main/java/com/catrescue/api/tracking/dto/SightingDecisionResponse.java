package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.DedupStatus;

public record SightingDecisionResponse(
        Long sightingId,
        Long catId,
        DedupStatus dedupStatus,
        Long suggestedCatId,
        Long duplicateOfSightingId,
        Double similarityScore,
        String dedupReason
) {
}
