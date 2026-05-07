package com.catrescue.api.tracking.dto;

public record DedupDecision(
        boolean duplicateLikely,
        Double bestSimilarityScore,
        Long matchedSightingId,
        Long suggestedCatId,
        String reason
) {
}
