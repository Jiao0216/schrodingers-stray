package com.catrescue.api.service;

import com.catrescue.api.domain.ModelLabels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EarTipNeedsReviewTest {

    @Test
    void unclearAlwaysNeedsReview() {
        assertThat(AssessmentService.earTipNeedsReview("unclear", "no")).isTrue();
        assertThat(AssessmentService.earTipNeedsReview("unclear", "yes")).isTrue();
    }

    @Test
    void contradictionWithVolunteerNeedsReview() {
        assertThat(AssessmentService.earTipNeedsReview("yes", "no")).isTrue();
        assertThat(AssessmentService.earTipNeedsReview("no", "yes")).isTrue();
    }

    @Test
    void agreementOrLegacyDoesNot() {
        assertThat(AssessmentService.earTipNeedsReview("yes", "yes")).isFalse();
        assertThat(AssessmentService.earTipNeedsReview("no", "no")).isFalse();
        assertThat(AssessmentService.earTipNeedsReview("", "no")).isFalse();
        assertThat(AssessmentService.earTipNeedsReview(null, null)).isFalse();
    }

    private static ModelLabels verdict(String v, boolean tipped) {
        return new ModelLabels(0, 0, 0.5, List.of(), tipped, tipped, "", "high", false, false,
                80, "", -1, -1, -1, -1, List.of(), "", v);
    }

    @Test
    void volunteerAnswerWinsOverAi() {
        assertThat(AssessmentService.effectiveEarClip("no", verdict("yes", true))).isEqualTo("no");
        assertThat(AssessmentService.effectiveEarClip("yes", verdict("no", false))).isEqualTo("yes");
    }

    @Test
    void noAnswerUsesAiVerdict() {
        assertThat(AssessmentService.effectiveEarClip("", verdict("yes", true))).isEqualTo("yes");
        assertThat(AssessmentService.effectiveEarClip(null, verdict("no", false))).isEqualTo("no");
        assertThat(AssessmentService.effectiveEarClip("", verdict("unclear", false))).isEqualTo("unclear");
        // Legacy result without a verdict and no tip seen: not known to be tipped.
        assertThat(AssessmentService.effectiveEarClip("", verdict("", false))).isEqualTo("unclear");
    }
}
