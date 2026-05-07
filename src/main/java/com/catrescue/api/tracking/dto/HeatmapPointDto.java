package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * One weighted sample for client-side heatmap rendering.
 */
public record HeatmapPointDto(
        double latitude,
        double longitude,
        /** Linear decay weight in [0,1]; only sightings within the last 30 days are returned. */
        double weight,
        Instant occurredAt
) {
}
