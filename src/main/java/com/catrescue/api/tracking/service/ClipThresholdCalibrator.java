package com.catrescue.api.tracking.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the CLIP cosine-distance threshold that best separates volunteer-confirmed same-cat pairs from rejected
 * ones (max F1 on "same cat", predicted when distance &lt; threshold). Pure function so it can be unit-tested.
 */
public final class ClipThresholdCalibrator {

    public record Sample(double distance, boolean sameCat) {
    }

    public record Metrics(double precision, double recall, double f1) {
    }

    /**
     * @param threshold  threshold to use (calibrated, or {@code fallback} when not enough labels)
     * @param calibrated false when {@code fallback} was kept
     * @param configured metrics of {@code fallback} on the same samples, for comparison (null without samples)
     * @param chosen     metrics of {@code threshold} (null without samples)
     */
    public record Result(
            double threshold,
            boolean calibrated,
            int positives,
            int negatives,
            Metrics configured,
            Metrics chosen,
            String reason
    ) {
    }

    private ClipThresholdCalibrator() {
    }

    public static Result calibrate(List<Sample> samples, double fallback, int minPerClass, double floor, double ceiling) {
        int positives = (int) samples.stream().filter(Sample::sameCat).count();
        int negatives = samples.size() - positives;
        Metrics configured = samples.isEmpty() ? null : evaluate(samples, fallback);
        if (positives < minPerClass || negatives < minPerClass) {
            return new Result(fallback, false, positives, negatives, configured, configured,
                    "Need at least " + minPerClass + " confirmed and " + minPerClass
                            + " rejected CLIP matches; have " + positives + " / " + negatives);
        }

        double best = fallback;
        Metrics bestMetrics = configured;
        for (double candidate : candidateThresholds(samples, floor, ceiling)) {
            Metrics m = evaluate(samples, candidate);
            boolean better = m.f1() > bestMetrics.f1() + 1e-9
                    || (Math.abs(m.f1() - bestMetrics.f1()) <= 1e-9
                    && Math.abs(candidate - fallback) < Math.abs(best - fallback));
            if (better) {
                best = candidate;
                bestMetrics = m;
            }
        }
        return new Result(best, true, positives, negatives, configured, bestMetrics,
                "Max F1 over " + samples.size() + " volunteer-labelled CLIP matches");
    }

    /** Midpoints between consecutive distinct distances, clamped to [floor, ceiling], plus the bounds themselves. */
    static List<Double> candidateThresholds(List<Sample> samples, double floor, double ceiling) {
        double[] sorted = samples.stream().mapToDouble(Sample::distance).sorted().distinct().toArray();
        List<Double> out = new ArrayList<>();
        out.add(floor);
        out.add(ceiling);
        for (int i = 0; i + 1 < sorted.length; i++) {
            out.add(clamp((sorted[i] + sorted[i + 1]) / 2.0, floor, ceiling));
        }
        return out;
    }

    static Metrics evaluate(List<Sample> samples, double threshold) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (Sample s : samples) {
            boolean predicted = s.distance() < threshold;
            if (predicted && s.sameCat()) {
                tp++;
            } else if (predicted) {
                fp++;
            } else if (s.sameCat()) {
                fn++;
            }
        }
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Metrics(precision, recall, f1);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
