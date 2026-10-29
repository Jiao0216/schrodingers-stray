package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.service.ClipThresholdCalibrator.Result;
import com.catrescue.api.tracking.service.ClipThresholdCalibrator.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ClipThresholdCalibratorTest {

    private static List<Sample> samples(double[] same, double[] different) {
        List<Sample> out = new ArrayList<>();
        for (double d : same) {
            out.add(new Sample(d, true));
        }
        for (double d : different) {
            out.add(new Sample(d, false));
        }
        return out;
    }

    @Test
    void keepsFallbackWhenTooFewLabels() {
        List<Sample> s = samples(new double[]{0.10, 0.12}, new double[]{0.50});
        Result r = ClipThresholdCalibrator.calibrate(s, 0.35, 5, 0.10, 0.60);
        assertThat(r.calibrated()).isFalse();
        assertThat(r.threshold()).isEqualTo(0.35);
        assertThat(r.positives()).isEqualTo(2);
        assertThat(r.negatives()).isEqualTo(1);
    }

    @Test
    void keepsFallbackWithNoLabels() {
        Result r = ClipThresholdCalibrator.calibrate(List.of(), 0.35, 5, 0.10, 0.60);
        assertThat(r.calibrated()).isFalse();
        assertThat(r.threshold()).isEqualTo(0.35);
        assertThat(r.configured()).isNull();
    }

    @Test
    void tightensThresholdWhenConfiguredOneLetsDifferentCatsThrough() {
        // Same cat 0.08-0.18, different cats 0.22-0.30: configured 0.35 flags every different cat.
        List<Sample> s = samples(
                new double[]{0.08, 0.10, 0.12, 0.15, 0.18},
                new double[]{0.22, 0.24, 0.26, 0.28, 0.30}
        );
        Result r = ClipThresholdCalibrator.calibrate(s, 0.35, 5, 0.10, 0.60);
        assertThat(r.calibrated()).isTrue();
        assertThat(r.threshold()).isCloseTo(0.20, within(1e-9));
        assertThat(r.chosen().f1()).isEqualTo(1.0);
        assertThat(r.configured().precision()).isEqualTo(0.5);
    }

    @Test
    void loosensThresholdWhenConfiguredOneMissesSameCat() {
        List<Sample> s = samples(
                new double[]{0.20, 0.30, 0.36, 0.40, 0.42},
                new double[]{0.50, 0.52, 0.55, 0.58, 0.59}
        );
        Result r = ClipThresholdCalibrator.calibrate(s, 0.35, 5, 0.10, 0.60);
        assertThat(r.threshold()).isCloseTo(0.46, within(1e-9));
        assertThat(r.chosen().recall()).isEqualTo(1.0);
    }

    @Test
    void clampsToCeiling() {
        List<Sample> s = samples(
                new double[]{0.62, 0.64, 0.66, 0.68, 0.70},
                new double[]{0.80, 0.82, 0.84, 0.86, 0.88}
        );
        Result r = ClipThresholdCalibrator.calibrate(s, 0.35, 5, 0.10, 0.60);
        assertThat(r.threshold()).isLessThanOrEqualTo(0.60);
    }

    @Test
    void prefersThresholdClosestToConfiguredOnTies() {
        // Perfect separation anywhere in (0.15, 0.40); the configured 0.35 already scores F1=1, so keep it.
        List<Sample> s = samples(
                new double[]{0.05, 0.08, 0.10, 0.12, 0.15},
                new double[]{0.40, 0.45, 0.50, 0.55, 0.58}
        );
        Result r = ClipThresholdCalibrator.calibrate(s, 0.35, 5, 0.10, 0.60);
        assertThat(r.threshold()).isEqualTo(0.35);
        assertThat(r.chosen().f1()).isEqualTo(1.0);
    }
}
