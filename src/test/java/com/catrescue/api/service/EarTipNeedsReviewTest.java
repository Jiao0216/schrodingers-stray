package com.catrescue.api.service;

import org.junit.jupiter.api.Test;

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
}
