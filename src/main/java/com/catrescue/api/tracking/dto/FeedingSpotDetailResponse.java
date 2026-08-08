package com.catrescue.api.tracking.dto;

import java.util.List;

public record FeedingSpotDetailResponse(
        FeedingSpotResponse spot,
        List<FeedingSpotRecordResponse> recentRecords
) {
}
