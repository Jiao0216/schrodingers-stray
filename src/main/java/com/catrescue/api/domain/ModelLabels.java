package com.catrescue.api.domain;

import java.util.List;

/**
 * Output of multimodal inference. Replace stub with real model response mapping.
 * Optional inference hints come from the vision JSON and are calibrated server-side.
 */
public record ModelLabels(
        double sickOrInjuredConfidence,
        double needsFeedingConfidence,
        double likelyNotNeuteredOrEarNotTippedConfidence,
        List<String> rationalePhrases,
        boolean likelyEarTipped,
        /** Standardized TNR ear-tip crop visible (flat top ~1/4 of one pinna). */
        boolean earTipDetected,
        /** Model-reported strength: high / medium / low (empty if not given). */
        String earTipConfidenceLevel,
        /** high / medium / low — used to avoid false "sick" from blur/noise. */
        String imageQualityLevel,
        /** Clean indoor scene — slight downward adjustment of illness probability. */
        boolean indoorCleanSettingHint,
    /** True only if clear acute injury/illness signs are visible (see vision prompt). */
    boolean acuteSymptomsVisible,
    /**
     * Ear-tip (TNR clip) confidence on a 0–100 scale from the vision model; {@code -1} when not provided
     * (e.g. legacy assessments).
     */
    int earTipConfidencePercent,
    /** One-line basis for the ear-tip verdict (Chinese or English). */
    String earTipBasis,
    /**
     * Visible body-condition / health scores from vision (0–100). {@code -1} when unknown (legacy rows).
     */
    int healthBodyNormalPercent,
    int healthUndernutritionPercent,
    int healthSeverelyEmaciatedPercent,
    int healthSuspectedInjuryPercent
) {
}
