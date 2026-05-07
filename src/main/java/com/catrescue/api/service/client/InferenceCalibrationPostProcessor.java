package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

import java.util.ArrayList;
import java.util.List;
/**
 * Applies deterministic rules after the vision model and {@link ModelLabelPostProcessor}
 * so illness/TNR scores are conservative for demos.
 */
public final class InferenceCalibrationPostProcessor {

    private static final double MAX_TNR_IF_JSON_EAR_TIP = 0.06;
    private static final double SOFT_CAP_SICK_NO_ACUTE = 0.48;
    private static final double CAP_SICK_LOW_IMAGE_QUALITY = 0.38;
    private static final double INDOOR_HEALTH_FACTOR = 0.85;

    private InferenceCalibrationPostProcessor() {
    }

    /**
     * Runs after {@link ModelLabelPostProcessor#adjustForEarTipEvidence(ModelLabels)}.
     */
    public static ModelLabels apply(ModelLabels labels) {
        double sick = labels.sickOrInjuredConfidence();
        double feed = labels.needsFeedingConfidence();
        double tnr = labels.likelyNotNeuteredOrEarNotTippedConfidence();
        boolean earTipped = labels.likelyEarTipped();

        List<String> rationale = labels.rationalePhrases() == null
                ? new ArrayList<>()
                : new ArrayList<>(labels.rationalePhrases());

        // Rule 1 — JSON ear tip overrides other spay/neuter uncertainty
        if (labels.earTipDetected() || earTipConfidenceStrong(labels.earTipConfidenceLevel())) {
            earTipped = true;
            tnr = Math.min(tnr, MAX_TNR_IF_JSON_EAR_TIP);
        }

        // Rule 2 — avoid false "sick" from blur / no acute signs
        String iq = safeQuality(labels.imageQualityLevel());
        if ("low".equalsIgnoreCase(iq) && !labels.acuteSymptomsVisible()) {
            sick = Math.min(sick, CAP_SICK_LOW_IMAGE_QUALITY);
            appendOnce(rationale, "uncertain health cues — image quality low; monitor unless symptoms worsen");
        }
        if (!labels.acuteSymptomsVisible()) {
            sick = Math.min(sick, SOFT_CAP_SICK_NO_ACUTE);
        }

        // Rule 3 — clean indoor default bias
        if (labels.indoorCleanSettingHint()) {
            sick = sick * INDOOR_HEALTH_FACTOR;
        }

        sick = clamp01(sick);
        feed = clamp01(feed);
        tnr = clamp01(tnr);

        return new ModelLabels(
                sick,
                feed,
                tnr,
                List.copyOf(rationale),
                earTipped,
                labels.earTipDetected(),
                labels.earTipConfidenceLevel(),
                labels.imageQualityLevel(),
                labels.indoorCleanSettingHint(),
                labels.acuteSymptomsVisible(),
                labels.earTipConfidencePercent(),
                labels.earTipBasis() != null ? labels.earTipBasis() : "",
                labels.healthBodyNormalPercent(),
                labels.healthUndernutritionPercent(),
                labels.healthSeverelyEmaciatedPercent(),
                labels.healthSuspectedInjuryPercent()
        );
    }

    private static boolean earTipConfidenceStrong(String level) {
        String l = safeLevel(level);
        return "high".equalsIgnoreCase(l);
    }

    private static String safeLevel(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeQuality(String s) {
        if (s == null || s.isBlank()) {
            return "medium";
        }
        return s.trim();
    }

    private static void appendOnce(List<String> rationale, String line) {
        String t = line.trim();
        for (String existing : rationale) {
            if (existing != null && existing.contains("uncertain health cues")) {
                return;
            }
        }
        rationale.add(t);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }
}
