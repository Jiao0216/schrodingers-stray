package com.catrescue.api.dto;

import java.util.List;

public record CatFeaturesDto(
        String coatColor,
        String patternType,
        String bodySize,
        List<String> specialFeatures,
        boolean earTipped
) {
}
