package com.catrescue.api.tracking.dto;

import java.time.Instant;
import java.util.List;

public record CatHistoryResponse(
        long catId,
        String displayName,
        boolean earTipped,
        String sterilizationStatus,
        Instant firstSeenAt,
        Instant lastSeenAt,
        double lastSeenLatitude,
        double lastSeenLongitude,
        List<CatHistorySightingDto> recentSightings
) {
}
