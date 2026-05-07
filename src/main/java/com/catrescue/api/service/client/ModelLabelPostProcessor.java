package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

import java.util.List;
import java.util.Locale;

/**
 * Post-processes multimodal labels for ear tipping (TNR clip): a visibly clipped / flat ear
 * means the cat is already sterilized; clearly intact ears suggest TNR may still be needed.
 */
public final class ModelLabelPostProcessor {

    /** If ear tipping is visible, "not neutered" score should stay below this. */
    private static final double MAX_TNR_WHEN_TIPPED = 0.10;
    /** If ears appear clearly intact (no clip), nudge TNR above routing threshold. */
    private static final double MIN_TNR_WHEN_INTACT = 0.55;

    /** Do not include vague phrases like "耳朵完整" here — they appear in mixed captions (one ear tipped). */
    private static final String[] NEGATION_BEFORE_TIP = {
            "not ear-tipped", "not ear tipped", "not tipped", "no ear tip", "no ear-tip",
            "未剪耳", "未见剪耳", "无剪耳", "没有剪耳", "没有耳标",
            "双耳未剪", "两边未剪", "两侧未剪"
    };

    private static final String[] EAR_TIPPED_HINTS = {
            // Avoid "ear tip" alone — it matches benign phrases like "symmetrical ear tips".
            "ear-tip", "ear-tipped", "eartip", "tipped ear",
            "ear notch", "notched ear", "flat ear", "clipped ear", "clip on ear",
            "剪耳", "耳朵被剪", "耳尖被剪", "耳尖平", "平耳", "耳尖缺口", "可见剪耳", "耳尖可见平切",
            "缺一角", "缺角", "耳尖缺失", "斜剪",
            "tnr标记", "绝育标记", "绝育耳标", "tnr耳标"
    };

    private static final String[] EAR_INTACT_HINTS = {
            "intact ear", "ears intact", "symmetrical ears", "no clip", "no ear clip",
            "no visible tip", "no notch", "ears appear normal", "round ear tips",
            "耳朵完整", "双耳完整", "耳型完整", "无平切"
    };

    private ModelLabelPostProcessor() {
    }

    public static ModelLabels adjustForEarTipEvidence(ModelLabels labels) {
        List<String> rationale = labels.rationalePhrases() == null ? List.of() : labels.rationalePhrases();
        String joined = String.join(" ", rationale).toLowerCase(Locale.ROOT);

        boolean uncertain = hasUncertainLanguage(joined);

        boolean earTippedModel = labels.likelyEarTipped();
        boolean earTippedText = !uncertain && matchesEarTippedEvidence(joined, rationale);
        boolean earTipped = earTippedModel || earTippedText;

        boolean intactText = !uncertain && matchesAnyHint(joined, EAR_INTACT_HINTS, rationale)
                && !matchesEarTippedEvidence(joined, rationale);

        if (earTipped && intactText) {
            if (earTippedModel) {
                intactText = false;
            } else {
                earTipped = false;
            }
        }

        double sick = labels.sickOrInjuredConfidence();
        double feed = labels.needsFeedingConfidence();
        double tnr = labels.likelyNotNeuteredOrEarNotTippedConfidence();
        boolean outFlag;

        if (earTipped) {
            tnr = Math.min(tnr, MAX_TNR_WHEN_TIPPED);
            outFlag = true;
        } else if (intactText) {
            tnr = Math.max(tnr, MIN_TNR_WHEN_INTACT);
            outFlag = false;
        } else {
            outFlag = legacyEarTipAdjust(joined, uncertain, labels);
            tnr = legacyTnrAdjust(tnr, outFlag);
        }

        return new ModelLabels(
                sick,
                feed,
                tnr,
                labels.rationalePhrases(),
                outFlag,
                labels.earTipDetected(),
                labels.earTipConfidenceLevel() != null ? labels.earTipConfidenceLevel() : "",
                labels.imageQualityLevel() != null ? labels.imageQualityLevel() : "medium",
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

    private static boolean legacyEarTipAdjust(String joined, boolean uncertain, ModelLabels labels) {
        boolean clearEarTipFromText = (joined.contains("ear tip clearly visible")
                || joined.contains("clearly ear-tipped")
                || joined.contains("clear ear-tip notch"))
                && !uncertain;

        return labels.likelyEarTipped() || clearEarTipFromText;
    }

    private static double legacyTnrAdjust(double tnr, boolean hasEarTipEvidence) {
        if (!hasEarTipEvidence) {
            return tnr;
        }
        return Math.min(tnr, MAX_TNR_WHEN_TIPPED);
    }

    /** Ear-tip cues from text, but not when negated (e.g. 未剪耳 contains 剪耳 as a substring). */
    private static boolean matchesEarTippedEvidence(String joinedLower, List<String> rationaleLines) {
        if (textImpliesNotTipped(joinedLower)) {
            return false;
        }
        return matchesAnyHint(joinedLower, EAR_TIPPED_HINTS, rationaleLines);
    }

    private static boolean hasUncertainLanguage(String joined) {
        return joined.contains("possibly")
                || joined.contains("maybe")
                || joined.contains("unclear")
                || joined.contains("not clear")
                || joined.contains("cannot determine")
                || joined.contains("unable to determine")
                || joined.contains("无法判断");
    }

    private static boolean textImpliesNotTipped(String joined) {
        for (String n : NEGATION_BEFORE_TIP) {
            if (joined.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyHint(String joinedLower, String[] hints, List<String> rationaleLines) {
        for (String h : hints) {
            String hl = h.toLowerCase(Locale.ROOT);
            if (joinedLower.contains(hl)) {
                return true;
            }
        }
        for (String line : rationaleLines) {
            String l = line.toLowerCase(Locale.ROOT);
            for (String h : hints) {
                if (l.contains(h.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
