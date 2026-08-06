package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.ClipDedupDecision;
import com.catrescue.api.tracking.dto.DedupDecision;

import java.util.Locale;

/**
 * Combines OpenAI feature and CLIP dedup signals (route B: either can trigger review).
 */
public final class DedupSignalMerger {

    private DedupSignalMerger() {
    }

    public record CombinedDedupDecision(
            boolean pendingReview,
            Long suggestedCatId,
            Long duplicateOfSightingId,
            Double featureSimilarityScore,
            String dedupReason
    ) {
    }

    public static CombinedDedupDecision merge(DedupDecision feature, ClipDedupDecision clip) {
        boolean pending = feature.duplicateLikely() || clip.duplicateLikely();

        Long suggestedCatId = null;
        Long duplicateSightingId = null;
        if (feature.duplicateLikely()) {
            suggestedCatId = feature.suggestedCatId();
            duplicateSightingId = feature.matchedSightingId();
        } else if (clip.duplicateLikely()) {
            suggestedCatId = clip.matchedCatId();
            duplicateSightingId = clip.matchedSightingId();
        }

        String reason = buildReason(feature, clip, pending);
        return new CombinedDedupDecision(
                pending,
                suggestedCatId,
                duplicateSightingId,
                feature.bestSimilarityScore(),
                reason
        );
    }

    static String buildReason(DedupDecision feature, ClipDedupDecision clip, boolean pending) {
        String featurePart = feature.duplicateLikely()
                ? "feature HIT score=" + format(feature.bestSimilarityScore())
                : "feature miss score=" + format(feature.bestSimilarityScore());
        String clipPart = clip.duplicateLikely()
                ? "clip HIT distance=" + format(clip.bestDistance())
                : "clip miss distance=" + format(clip.bestDistance());

        if (!pending) {
            return featurePart + "; " + clipPart + "; no signal above threshold";
        }
        return featurePart + "; " + clipPart + "; pending volunteer review";
    }

    private static String format(Double value) {
        if (value == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
