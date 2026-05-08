package com.catrescue.api.tracking.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record VolunteerSavedCatEntryDto(
        long id,
        long catId,
        Instant savedAt,
        JsonNode snapshot
) {
}
