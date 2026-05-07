package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.DedupStatus;

import java.time.Instant;

/**
 * Heatmap point enriched with sighting/media details for popup display.
 */
public record HeatmapSightingResponse(
        Long sightingId,
        Long catId,
        double lat,
        double lng,
        double weight,
        Instant occurredAt,
        String imageUrl,
        String coatColor,
        String patternType,
        String bodySize,
        boolean earTipped,
        Double similarityScore,
        DedupStatus dedupStatus,
        boolean sameCatByMultimodal,
        boolean feedingOverdue
) {
}
