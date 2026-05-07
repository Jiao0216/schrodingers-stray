package com.catrescue.api.tracking.dto;

import jakarta.validation.constraints.NotNull;

public record FeedingCheckInRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        Long feedingStationId,
        String note
) {
}
