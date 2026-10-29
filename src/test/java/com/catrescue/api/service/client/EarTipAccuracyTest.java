package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.service.client.EarTipJudgement.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EarTipAccuracyTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static Vote v(String verdict, int conf) {
        return new Vote(verdict, conf, "依据", "basis");
    }

    private static ModelLabels labels(boolean tipped, String level, String verdict, List<String> rationale) {
        return new ModelLabels(0.2, 0, 0.5, rationale, tipped, tipped, level, "high", false, false,
                80, "", 80, 10, 5, 5, List.of(), "", verdict);
    }

    // --- vote aggregation -------------------------------------------------------------------------------------

    @Test
    void unanimousConfidentYesIsYes() {
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 85), v("yes", 90), v("yes", 80)), 75, "m");
        assertThat(j.verdict()).isEqualTo("yes");
        assertThat(j.confidencePercent()).isEqualTo(85);
        assertThat(j.votesYes()).isEqualTo(3);
    }

    @Test
    void yesMajorityBelowConfidenceGateIsUnclear() {
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 60), v("yes", 65), v("no", 70)), 75, "m");
        assertThat(j.verdict()).isEqualTo("unclear");
        assertThat(j.confidencePercent()).isLessThanOrEqualTo(40);
    }

    @Test
    void twoOfThreeConfidentYesIsYes() {
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 90), v("yes", 80), v("no", 70)), 75, "m");
        assertThat(j.verdict()).isEqualTo("yes");
        assertThat(j.votesNo()).isEqualTo(1);
    }

    @Test
    void splitVotesAreUnclear() {
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 90), v("no", 90), v("unclear", 30)), 75, "m");
        assertThat(j.verdict()).isEqualTo("unclear");
        assertThat(j.basisZh()).contains("请现场核实");
        assertThat(j.basisEn()).contains("verify the ears in person");
    }

    @Test
    void noMajorityNeedsNoConfidenceGate() {
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("no", 50), v("no", 55), v("unclear", 20)), 75, "m");
        assertThat(j.verdict()).isEqualTo("no");
    }

    @Test
    void noVotesIsUnclear() {
        assertThat(EarTipJudgement.aggregate(List.of(), 75, "m").verdict()).isEqualTo("unclear");
    }

    // --- single vote consistency ------------------------------------------------------------------------------

    @Test
    void voteVerdictFollowsPerEarAnswers() throws Exception {
        Vote yes = OpenAiMultimodalClient.parseVote(M.readTree(
                "{\"left_ear\":\"tipped\",\"right_ear\":\"intact\",\"verdict\":\"yes\",\"confidence_percent\":88}"));
        assertThat(yes.verdict()).isEqualTo("yes");
        assertThat(yes.confidencePercent()).isEqualTo(88);

        Vote no = OpenAiMultimodalClient.parseVote(M.readTree(
                "{\"left_ear\":\"intact\",\"right_ear\":\"intact\",\"verdict\":\"no\",\"confidence_percent\":70}"));
        assertThat(no.verdict()).isEqualTo("no");

        Vote hidden = OpenAiMultimodalClient.parseVote(M.readTree(
                "{\"left_ear\":\"intact\",\"right_ear\":\"not_visible\",\"verdict\":\"unclear\",\"confidence_percent\":30}"));
        assertThat(hidden.verdict()).isEqualTo("unclear");
    }

    @Test
    void selfContradictingVoteIsUnclear() throws Exception {
        // Says "yes" but reports both ears intact.
        Vote v = OpenAiMultimodalClient.parseVote(M.readTree(
                "{\"left_ear\":\"intact\",\"right_ear\":\"intact\",\"verdict\":\"yes\",\"confidence_percent\":95}"));
        assertThat(v.verdict()).isEqualTo("unclear");
        // One ear hidden can never be "no".
        Vote v2 = OpenAiMultimodalClient.parseVote(M.readTree(
                "{\"left_ear\":\"intact\",\"right_ear\":\"not_visible\",\"verdict\":\"no\",\"confidence_percent\":80}"));
        assertThat(v2.verdict()).isEqualTo("unclear");
    }

    // --- verdict is authoritative in post-processing ----------------------------------------------------------

    @Test
    void rationaleWordingDoesNotOverrideNoVerdict() {
        ModelLabels in = labels(false, "high", "no", List.of("左耳有剪耳标记，右耳无法确认。"));
        ModelLabels out = InferenceCalibrationPostProcessor.apply(ModelLabelPostProcessor.adjustForEarTipEvidence(in));
        assertThat(out.likelyEarTipped()).isFalse();
        assertThat(out.earTipVerdict()).isEqualTo("no");
    }

    @Test
    void highConfidenceAloneIsNotAnEarTip() {
        // High confidence that the ears are intact must not mark the cat as tipped (old calibration rule 1 bug).
        ModelLabels in = labels(false, "high", "", List.of("ears look normal"));
        ModelLabels out = InferenceCalibrationPostProcessor.apply(in);
        assertThat(out.likelyEarTipped()).isFalse();
    }

    @Test
    void unclearVerdictIsNotTippedAndKeepsTnrScore() {
        ModelLabels in = labels(false, "low", "unclear", List.of("耳尖可见平切"));
        ModelLabels out = ModelLabelPostProcessor.adjustForEarTipEvidence(in);
        assertThat(out.likelyEarTipped()).isFalse();
        assertThat(out.likelyNotNeuteredOrEarNotTippedConfidence()).isEqualTo(0.5);
    }

    @Test
    void judgementOverridesMainAnalysis() {
        ModelLabels main = labels(false, "high", "no", List.of("双耳完整"));
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 90), v("yes", 85), v("yes", 80)), 75, "m");
        ModelLabels out = InferenceCalibrationPostProcessor.apply(
                ModelLabelPostProcessor.adjustForEarTipEvidence(j.applyTo(main)));
        assertThat(out.earTipVerdict()).isEqualTo("yes");
        assertThat(out.likelyEarTipped()).isTrue();
        assertThat(out.earTipBasis()).isEqualTo("依据");
        assertThat(out.likelyNotNeuteredOrEarNotTippedConfidence()).isLessThanOrEqualTo(0.10);
    }

    @Test
    void judgementReplacesContradictingEarSentencesInRationale() {
        ModelLabels main = new ModelLabels(0.2, 0, 0.5,
                List.of("右耳有平整的切口", "整体健康状况良好"), true, true, "high", "high", false, false,
                80, "右耳平剪", 80, 10, 5, 5,
                List.of("Right ear has a flat cut", "Overall body condition good"), "right ear cut", "yes");
        EarTipJudgement j = EarTipJudgement.aggregate(List.of(v("yes", 90), v("no", 90), v("unclear", 30)), 75, "m");
        ModelLabels out = j.applyTo(main);
        assertThat(out.earTipVerdict()).isEqualTo("unclear");
        assertThat(out.rationalePhrases()).noneMatch(p -> p.contains("右耳有平整"))
                .contains("整体健康状况良好").first().asString().contains("请现场核实");
        assertThat(out.rationalePhrasesEn()).noneMatch(p -> p.contains("flat cut"))
                .contains("Overall body condition good");
    }
}
