package com.catrescue.api.tracking.dto;

public record FeedingSpotCheckInRequest(
        Long fedByUserId,
        String note
) {
}
