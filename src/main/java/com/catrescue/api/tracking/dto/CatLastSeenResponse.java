package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * Aggregated last-seen summary for a cat profile plus the latest sighting reporter.
 */
public record CatLastSeenResponse(
        Long catId,
        Instant lastSeenAt,
        double lastSeenLatitude,
        double lastSeenLongitude,
        Long lastSightingId,
        Long lastReporterUserId,
        String lastAddressText
) {
}
