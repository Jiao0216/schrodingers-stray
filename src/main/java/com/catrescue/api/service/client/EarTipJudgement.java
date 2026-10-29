package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of the dedicated ear-tip (TNR clip) check: several independent model votes aggregated into one verdict.
 *
 * <p>The rule is deliberately asymmetric. A false "yes" (calling an intact cat ear-tipped) makes volunteers skip a
 * cat that still needs TNR, so "yes" needs a strong agreeing majority with high confidence; anything short of a
 * clear majority is "unclear", which the UI turns into a request for human confirmation.
 */
public record EarTipJudgement(
        /** "yes" | "no" | "unclear" */
        String verdict,
        /** 0–100; mean confidence of the votes behind the verdict (0 when unclear without agreement). */
        int confidencePercent,
        String basisZh,
        String basisEn,
        int votesYes,
        int votesNo,
        int votesUnclear,
        String model
) {

    /** One model answer. {@code verdict} is "yes" | "no" | "unclear". */
    public record Vote(String verdict, int confidencePercent, String basisZh, String basisEn) {
    }

    public static EarTipJudgement aggregate(List<Vote> votes, int yesMinConfidence, String model) {
        List<Vote> yes = new ArrayList<>();
        List<Vote> no = new ArrayList<>();
        int unclear = 0;
        for (Vote v : votes) {
            switch (v.verdict() == null ? "" : v.verdict()) {
                case "yes" -> yes.add(v);
                case "no" -> no.add(v);
                default -> unclear++;
            }
        }
        int n = votes.size();
        if (n == 0) {
            return new EarTipJudgement("unclear", 0, "", "", 0, 0, 0, model);
        }
        // Strict two-thirds majority (2 of 3, 3 of 4, 4 of 5…).
        int needed = (int) Math.ceil(n * 2.0 / 3.0);

        if (yes.size() >= needed && meanConfidence(yes) >= yesMinConfidence) {
            return of("yes", yes, no.size(), unclear, model);
        }
        if (no.size() >= needed) {
            return of("no", no, yes.size(), unclear, model);
        }
        // Disagreement, too many "unclear" votes, or a "yes" majority that is not confident enough.
        Vote sample = !yes.isEmpty() ? yes.get(0) : (!no.isEmpty() ? no.get(0) : votes.get(0));
        return new EarTipJudgement(
                "unclear",
                Math.min(40, Math.max(meanConfidence(yes), meanConfidence(no))),
                "多次判断结果不一致或把握不足，请现场核实耳朵。" + suffix(sample.basisZh()),
                "Repeated checks disagreed or were not confident — please verify the ears in person." + suffixEn(sample.basisEn()),
                yes.size(),
                no.size(),
                unclear,
                model
        );
    }

    private static EarTipJudgement of(String verdict, List<Vote> agreeing, int other, int unclear, String model) {
        Vote first = agreeing.get(0);
        int yesCount = "yes".equals(verdict) ? agreeing.size() : other;
        int noCount = "no".equals(verdict) ? agreeing.size() : other;
        return new EarTipJudgement(
                verdict,
                meanConfidence(agreeing),
                first.basisZh() == null ? "" : first.basisZh(),
                first.basisEn() == null ? "" : first.basisEn(),
                yesCount,
                noCount,
                unclear,
                model
        );
    }

    private static int meanConfidence(List<Vote> votes) {
        if (votes.isEmpty()) {
            return 0;
        }
        return (int) Math.round(votes.stream().mapToInt(v -> clamp(v.confidencePercent())).average().orElse(0));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String suffix(String basis) {
        return basis == null || basis.isBlank() ? "" : "（其中一次：" + basis + "）";
    }

    private static String suffixEn(String basis) {
        return basis == null || basis.isBlank() ? "" : " (one check: " + basis + ")";
    }

    /**
     * Overrides the ear-tip fields of the main analysis with this verdict. The verdict is authoritative: the
     * post-processors no longer second-guess it from rationale wording.
     */
    public ModelLabels applyTo(ModelLabels m) {
        boolean tipped = "yes".equals(verdict);
        String level = "unclear".equals(verdict) ? "low" : (confidencePercent >= 70 ? "high" : confidencePercent >= 40 ? "medium" : "low");
        return new ModelLabels(
                m.sickOrInjuredConfidence(),
                m.needsFeedingConfidence(),
                m.likelyNotNeuteredOrEarNotTippedConfidence(),
                m.rationalePhrases(),
                tipped,
                tipped,
                level,
                m.imageQualityLevel(),
                m.indoorCleanSettingHint(),
                m.acuteSymptomsVisible(),
                confidencePercent,
                basisZh,
                m.healthBodyNormalPercent(),
                m.healthUndernutritionPercent(),
                m.healthSeverelyEmaciatedPercent(),
                m.healthSuspectedInjuryPercent(),
                m.rationalePhrasesEn(),
                basisEn,
                verdict
        );
    }
}
