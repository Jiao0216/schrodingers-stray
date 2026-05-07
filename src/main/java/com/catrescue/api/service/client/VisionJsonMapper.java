package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps multimodal JSON output into {@link ModelLabels} before rule-based post-processing.
 */
public final class VisionJsonMapper {

    private VisionJsonMapper() {
    }

    public static ModelLabels parse(JsonNode out) {
        int hNormal = clampPercent(out.path("health_body_normal_percent").asInt(-1));
        int hUnder = clampPercent(out.path("health_undernutrition_percent").asInt(-1));
        int hSevere = clampPercent(out.path("health_severely_emaciated_percent").asInt(-1));
        int hInjury = clampPercent(out.path("health_suspected_injury_percent").asInt(-1));

        boolean hasNewHealth = hNormal >= 0 || hUnder >= 0 || hSevere >= 0 || hInjury >= 0;
        double feed = clamp01(out.path("needsFeedingConfidence").asDouble(0));
        if (hasNewHealth) {
            feed = 0;
        } else {
            feed = clamp01(out.path("needsFeedingConfidence").asDouble(0.22));
        }

        double sick;
        if (hasNewHealth) {
            double maxAbnormal = Math.max(Math.max(hInjury, hSevere), hUnder) / 100.0;
            sick = clamp01(maxAbnormal);
        } else {
            sick = clamp01(out.path("sickOrInjuredConfidence").asDouble(0.12));
        }

        double tnr = clamp01(out.path("likelyNotNeuteredOrEarNotTippedConfidence").asDouble(0.35));

        List<String> rationale = new ArrayList<>();
        JsonNode arr = out.path("rationalePhrases");
        if (arr.isArray()) {
            arr.forEach(n -> {
                if (n.isTextual()) {
                    rationale.add(n.asText());
                }
            });
        }
        if (rationale.isEmpty()) {
            rationale.add("Model did not provide rationale; verify in person.");
        }

        String verdictRaw = out.path("ear_tip_verdict").asText("");
        String verdict = normalizeVerdict(verdictRaw);

        int percentRaw = out.path("ear_tip_confidence_percent").asInt(-1);
        int earTipPercent = clampPercent(percentRaw);

        String basis = out.path("ear_tip_basis").asText("").trim();

        boolean likelyEarTipped = out.path("likelyEarTipped").asBoolean(false);
        boolean earTipDetected = out.path("ear_tip_detected").asBoolean(false);

        if (!verdict.isEmpty()) {
            if ("yes".equals(verdict)) {
                likelyEarTipped = true;
                earTipDetected = true;
            } else {
                likelyEarTipped = false;
                earTipDetected = false;
            }
        } else {
            if (!likelyEarTipped) {
                likelyEarTipped = rationale.stream()
                        .map(String::toLowerCase)
                        .anyMatch(VisionJsonMapper::rationaleImpliesEarTipped);
            }
            if (likelyEarTipped) {
                earTipDetected = true;
            }
        }

        boolean leftEarTnrMark = out.path("left_ear_tnr_mark").asBoolean(false);
        boolean rightEarTnrMark = out.path("right_ear_tnr_mark").asBoolean(false);
        if (leftEarTnrMark || rightEarTnrMark) {
            likelyEarTipped = true;
            earTipDetected = true;
        }

        String earTipConfidence = normalizeTier(out.path("ear_tip_confidence").asText(""));
        if (earTipConfidence.isEmpty()) {
            earTipConfidence = deriveTierFromVerdictAndPercent(verdict, earTipPercent);
        }
        if ("unclear".equals(verdict)) {
            earTipConfidence = "low";
        }

        String imageQuality = normalizeQuality(out.path("image_quality").asText("medium"));
        boolean indoorClean = out.path("indoor_clean_setting").asBoolean(false);
        boolean acuteSymptomsVisible = out.path("acute_symptoms_visible").asBoolean(false);

        if (!basis.isEmpty()) {
            rationale.add(0, basis);
        }

        int outNormal = hasNewHealth ? hNormal : -1;
        int outUnder = hasNewHealth ? hUnder : -1;
        int outSevere = hasNewHealth ? hSevere : -1;
        int outInjury = hasNewHealth ? hInjury : -1;

        return new ModelLabels(
                sick,
                feed,
                tnr,
                List.copyOf(rationale),
                likelyEarTipped,
                earTipDetected,
                earTipConfidence,
                imageQuality,
                indoorClean,
                acuteSymptomsVisible,
                earTipPercent,
                basis.isEmpty() ? "" : basis,
                outNormal,
                outUnder,
                outSevere,
                outInjury
        );
    }

    private static String normalizeVerdict(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("yes") || s.equals("y") || s.equals("是") || s.equals("true")) {
            return "yes";
        }
        if (s.equals("no") || s.equals("n") || s.equals("否") || s.equals("false")) {
            return "no";
        }
        if (s.equals("unclear") || s.equals("unknown") || s.equals("无法确认") || s.equals("不确定")) {
            return "unclear";
        }
        return "";
    }

    private static int clampPercent(int v) {
        if (v < 0) {
            return -1;
        }
        return Math.max(0, Math.min(100, v));
    }

    private static String deriveTierFromVerdictAndPercent(String verdict, int percent) {
        if ("unclear".equals(verdict) || percent < 0) {
            return "";
        }
        if (percent >= 70) {
            return "high";
        }
        if (percent >= 40) {
            return "medium";
        }
        return "low";
    }

    private static String normalizeTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("high") || s.equals("medium") || s.equals("low")) {
            return s;
        }
        return "";
    }

    private static String normalizeQuality(String raw) {
        if (raw == null || raw.isBlank()) {
            return "medium";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("high") || s.equals("medium") || s.equals("low")) {
            return s;
        }
        return "medium";
    }

    private static boolean rationaleImpliesEarTipped(String s) {
        if (s.contains("未剪耳") || s.contains("无剪耳") || s.contains("未见剪耳")) {
            return false;
        }
        return s.contains("ear tip clearly visible")
                || s.contains("clear ear-tip notch")
                || s.contains("clearly ear-tipped")
                || s.contains("剪耳")
                || s.contains("耳尖平")
                || s.contains("平耳");
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }
}
