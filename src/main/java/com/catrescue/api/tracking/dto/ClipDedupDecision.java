package com.catrescue.api.tracking.dto;

/**
 * CLIP vector nearest-neighbor dedup signal (parallel to {@link DedupDecision}).
 */
public record ClipDedupDecision(
        boolean duplicateLikely,
        Double bestDistance,
        Long matchedSightingId,
        Long matchedCatId,
        String reason
) {
    public static ClipDedupDecision unavailable(String reason) {
        return new ClipDedupDecision(false, null, null, null, reason);
    }
}
