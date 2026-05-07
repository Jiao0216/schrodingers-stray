package com.catrescue.api.domain;

import com.catrescue.api.tracking.dto.CatFeatureVector;

import java.util.List;

/**
 * Persisted multimodal cat appearance features used for community duplicate detection.
 */
public record CatFeatureSnapshot(
        String coatColor,
        String patternType,
        String bodySize,
        List<String> specialFeatures,
        boolean earTipped
) {
    public static CatFeatureSnapshot fromVector(CatFeatureVector v) {
        if (v == null) {
            return new CatFeatureSnapshot("unknown", "unknown", "unknown", List.of(), false);
        }
        List<String> feats = v.specialFeatures() != null ? List.copyOf(v.specialFeatures()) : List.of();
        return new CatFeatureSnapshot(
                v.coatColor(),
                v.patternType(),
                v.bodySize(),
                feats,
                v.earTipped()
        );
    }
}
