package com.catrescue.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI handling guidance derived from the nearest completed assessment upload
 * (time/space heuristic aligned with sighting image fallback).
 */
public record CatProfileAiGuidanceResponse(
        UUID assessmentId,
        Instant assessmentCreatedAt,
        String branchType,
        /** Machine hint: {@code NEAR_SIGHTING} or {@code NEAR_PROFILE}. */
        String matchKind,
        String disclaimer,
        List<String> rescueNextSteps,
        List<String> healthGuidanceLines,
        List<String> rationalePhrases
) {
}
