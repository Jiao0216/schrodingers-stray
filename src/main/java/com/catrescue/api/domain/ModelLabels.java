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
    int healthSuspectedInjuryPercent,
    /**
     * English versions of {@link #rationalePhrases} / {@link #earTipBasis} (which are Simplified Chinese), so the UI
     * can show the model's reasoning in the selected language. Empty when the model did not provide them.
     */
    List<String> rationalePhrasesEn,
    String earTipBasisEn,
    /**
     * Final ear-tip verdict: "yes" | "no" | "unclear", or "" for legacy results. When set it is authoritative —
     * post-processors must not override it from rationale wording.
     */
    String earTipVerdict
) {

    /** Bilingual shape without an explicit verdict (legacy / fallback parsing). */
    public ModelLabels(
            double sickOrInjuredConfidence,
            double needsFeedingConfidence,
            double likelyNotNeuteredOrEarNotTippedConfidence,
            List<String> rationalePhrases,
            boolean likelyEarTipped,
            boolean earTipDetected,
            String earTipConfidenceLevel,
            String imageQualityLevel,
            boolean indoorCleanSettingHint,
            boolean acuteSymptomsVisible,
            int earTipConfidencePercent,
            String earTipBasis,
            int healthBodyNormalPercent,
            int healthUndernutritionPercent,
            int healthSeverelyEmaciatedPercent,
            int healthSuspectedInjuryPercent,
            List<String> rationalePhrasesEn,
            String earTipBasisEn
    ) {
        this(sickOrInjuredConfidence, needsFeedingConfidence, likelyNotNeuteredOrEarNotTippedConfidence, rationalePhrases,
                likelyEarTipped, earTipDetected, earTipConfidenceLevel, imageQualityLevel, indoorCleanSettingHint,
                acuteSymptomsVisible, earTipConfidencePercent, earTipBasis, healthBodyNormalPercent,
                healthUndernutritionPercent, healthSeverelyEmaciatedPercent, healthSuspectedInjuryPercent,
                rationalePhrasesEn, earTipBasisEn, "");
    }

    /** Pre-bilingual shape: English rationale / basis left empty. */
    public ModelLabels(
            double sickOrInjuredConfidence,
            double needsFeedingConfidence,
            double likelyNotNeuteredOrEarNotTippedConfidence,
            List<String> rationalePhrases,
            boolean likelyEarTipped,
            boolean earTipDetected,
            String earTipConfidenceLevel,
            String imageQualityLevel,
            boolean indoorCleanSettingHint,
            boolean acuteSymptomsVisible,
            int earTipConfidencePercent,
            String earTipBasis,
            int healthBodyNormalPercent,
            int healthUndernutritionPercent,
            int healthSeverelyEmaciatedPercent,
            int healthSuspectedInjuryPercent
    ) {
        this(sickOrInjuredConfidence, needsFeedingConfidence, likelyNotNeuteredOrEarNotTippedConfidence, rationalePhrases,
                likelyEarTipped, earTipDetected, earTipConfidenceLevel, imageQualityLevel, indoorCleanSettingHint,
                acuteSymptomsVisible, earTipConfidencePercent, earTipBasis, healthBodyNormalPercent,
                healthUndernutritionPercent, healthSeverelyEmaciatedPercent, healthSuspectedInjuryPercent,
                List.of(), "", "");
    }
}
