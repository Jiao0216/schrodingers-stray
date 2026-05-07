package com.catrescue.api.tracking.dto;

public record NearbyHelpCatDto(
        long catId,
        String displayName,
        /** Null when coordinates are redacted for C-end clients. */
        Double lastSeenLatitude,
        Double lastSeenLongitude,
        double distanceMeters,
        double priorityScore,
        String sterilizationStatus,
        boolean earTipped
) {
}
