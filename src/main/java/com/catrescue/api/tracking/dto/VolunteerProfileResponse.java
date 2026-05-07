package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record VolunteerProfileResponse(
        long id,
        String email,
        String displayName,
        String phone,
        String bio,
        Double serviceLatitude,
        Double serviceLongitude,
        boolean notifyNearbyEnabled,
        long volunteerPoints,
        Instant createdAt,
        VolunteerStatsResponse stats
) {
}
