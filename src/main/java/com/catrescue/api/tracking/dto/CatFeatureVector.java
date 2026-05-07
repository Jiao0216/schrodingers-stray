package com.catrescue.api.tracking.dto;

import java.util.List;

public record CatFeatureVector(
        String coatColor,
        String patternType,
        String bodySize,
        List<String> specialFeatures,
        boolean earTipped,
        double earTippedConfidence,
        double extractionConfidence,
        String summary
) {
}
