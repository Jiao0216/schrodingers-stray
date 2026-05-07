package com.catrescue.api.tracking.dto;

import java.time.Instant;
import java.util.List;

/**
 * Read model for a single cat's archive header (features + sighting counts in the heatmap window).
 * Detailed points: {@code GET /api/v1/cats/{id}/heatmap/sightings}.
 */
public record CatProfileResponse(
        long id,
        String displayName,
        String coatColor,
        String patternType,
        String bodySize,
        boolean earTipped,
        String sterilizationStatus,
        List<String> specialFeatures,
        Instant firstSeenAt,
        Instant lastSeenAt,
        double lastSeenLatitude,
        double lastSeenLongitude,
        int sightingsInWindowDays,
        /** Most recent feeding time, or null if none recorded. */
        Instant lastFedAt
) {
}
