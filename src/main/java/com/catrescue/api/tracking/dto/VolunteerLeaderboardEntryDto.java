package com.catrescue.api.tracking.dto;

public record VolunteerLeaderboardEntryDto(
        int rank,
        long userId,
        String displayName,
        long volunteerPoints
) {
}
