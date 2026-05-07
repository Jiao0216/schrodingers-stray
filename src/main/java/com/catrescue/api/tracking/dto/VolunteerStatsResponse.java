package com.catrescue.api.tracking.dto;

public record VolunteerStatsResponse(
        long sightingReports,
        long feedingCheckIns,
        long badgeCount
) {
}
