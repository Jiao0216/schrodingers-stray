package com.catrescue.api.dto;

import java.util.List;

public record ModelLabelsDto(
        double sickOrInjuredConfidence,
        double needsFeedingConfidence,
        double likelyNotNeuteredOrEarNotTippedConfidence,
        List<String> rationalePhrases,
        boolean likelyAlreadySterilized,
        String captureAdvice,
        boolean earTipDetected,
        String earTipConfidence,
        String imageQualityLevel,
        boolean acuteSymptomsVisible,
        /** 0–100 from vision model; null when unknown (legacy rows). */
        Integer earTipConfidencePercent,
        String earTipBasis,
        Integer healthBodyNormalPercent,
        Integer healthUndernutritionPercent,
        Integer healthSeverelyEmaciatedPercent,
        Integer healthSuspectedInjuryPercent
) {
}
