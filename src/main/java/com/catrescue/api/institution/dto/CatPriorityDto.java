package com.catrescue.api.institution.dto;

import com.catrescue.api.tracking.domain.SterilizationStatus;

import java.time.Instant;

public record CatPriorityDto(
        long catId,
        String displayName,
        double lastSeenLat,
        double lastSeenLng,
        Instant lastSeenAt,
        boolean earTipped,
        SterilizationStatus sterilizationStatus,
        double priorityScore,
        String priorityReason
) {
}
