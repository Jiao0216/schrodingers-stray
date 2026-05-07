package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * One archive cat matched by multimodal feature similarity (photo vs stored sighting features).
 */
public record CatPhotoMatchItemResponse(
        long catId,
        /** 0..1 feature similarity score */
        double similarityScore,
        /** Representative photo URL (latest sighting image used for scoring). */
        String imageUrl,
        /** Short location text: address when present, else approximate coordinates. */
        String locationSummary,
        double latitude,
        double longitude,
        Instant lastSeenAt
) {
}
