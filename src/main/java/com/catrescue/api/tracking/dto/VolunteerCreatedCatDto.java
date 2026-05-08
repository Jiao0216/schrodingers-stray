package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * Cats whose archive row was created by this volunteer (may exist without a separate sighting row).
 */
public record VolunteerCreatedCatDto(long catId, Instant lastSeenAt) {
}
