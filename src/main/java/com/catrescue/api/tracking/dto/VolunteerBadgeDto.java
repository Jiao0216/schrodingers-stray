package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.VolunteerBadgeCode;

import java.time.Instant;

public record VolunteerBadgeDto(
        VolunteerBadgeCode code,
        Instant earnedAt
) {
}
