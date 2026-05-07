package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record VolunteerRescueRecordDto(
        long sightingId,
        Long catId,
        Instant occurredAt,
        double latitude,
        double longitude,
        String addressText,
        String dedupStatus
) {
}
