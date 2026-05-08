package com.catrescue.api.tracking.dto;

import com.catrescue.api.dto.CatProfileAiGuidanceResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Frozen bundle written when a volunteer saves a cat profile to their hub.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VolunteerCatHubSnapshot(
        Instant capturedAt,
        CatProfileResponse profile,
        List<HeatmapSightingResponse> sightings,
        CatProfileAiGuidanceResponse aiGuidance
) {
}
