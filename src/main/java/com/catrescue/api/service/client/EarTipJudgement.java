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
        // Disagreement, too many "unclear" votes, or a "yes" majority that is not confident enough. No single vote is
        // quoted: repeating one vote's "clear cut" claim would read as a conclusion (and the agent would repeat it).
        return new EarTipJudgement(
                "unclear",
                Math.min(40, Math.max(meanConfidence(yes), meanConfidence(no))),
                "多次判断结果不一致或把握不足，请现场核实耳朵。",
                "Repeated checks disagreed or were not confident — please verify the ears in person.",
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

    /**
     * Overrides the ear-tip fields of the main analysis with this verdict. The verdict is authoritative: the
     * post-processors no longer second-guess it from rationale wording.
     */
    public ModelLabels applyTo(ModelLabels m) {
        boolean tipped = "yes".equals(verdict);
        String level = "unclear".equals(verdict) ? "low" : (confidencePercent >= 70 ? "high" : confidencePercent >= 40 ? "medium" : "low");
        // The main analysis' own ear sentences would contradict this verdict (and are passed on to the agent),
        // so they are replaced by this check's basis.
        List<String> zh = withoutEarPhrases(m.rationalePhrases(), true);
        List<String> en = withoutEarPhrases(m.rationalePhrasesEn(), false);
        if (basisZh != null && !basisZh.isBlank()) {
            zh.add(0, basisZh);
        }
        if (basisEn != null && !basisEn.isBlank()) {
            en.add(0, basisEn);
        }
        return new ModelLabels(
                m.sickOrInjuredConfidence(),
                m.needsFeedingConfidence(),
                m.likelyNotNeuteredOrEarNotTippedConfidence(),
                List.copyOf(zh),
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
                List.copyOf(en),
                basisEn,
                verdict
        );
    }

    private static final java.util.regex.Pattern EAR_EN = java.util.regex.Pattern.compile(
            "\\bears?\\b|ear-tip|eartip|\\btnr\\b|\\btipped\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    static List<String> withoutEarPhrases(List<String> phrases, boolean zh) {
        List<String> out = new ArrayList<>();
        if (phrases == null) {
            return out;
        }
        for (String p : phrases) {
            if (p == null || p.isBlank()) {
                continue;
            }
            boolean aboutEars = zh ? (p.contains("耳") || p.toLowerCase().contains("tnr")) : EAR_EN.matcher(p).find();
            if (!aboutEars) {
                out.add(p);
            }
        }
        return out;
    }
}
