package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.VolunteerBadgeCode;

import java.time.Instant;
import java.util.List;

public record FeedingCheckInResponse(
        long checkInId,
        int pointsAwarded,
        long totalPoints,
        Instant createdAt,
        List<VolunteerBadgeCode> newlyEarnedBadges
) {
}
