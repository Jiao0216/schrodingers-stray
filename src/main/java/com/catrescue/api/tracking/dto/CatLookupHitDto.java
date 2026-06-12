package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * One cat matched near a coordinate lookup (for AI / volunteer tools).
 */
public record CatLookupHitDto(
        long catId,
        Instant lastSeenAt,
        double latitude,
        double longitude,
        /** Approximate distance from lookup point in meters. */
        double distanceMeters,
        String addressText,
        boolean earTipped,
        double earTippedConfidence,
        String displayName,
        String sterilizationStatus
) {
}
