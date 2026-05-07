package com.catrescue.api.tracking.service;

import com.catrescue.api.domain.CatFeatureSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Same weighted blend as {@link SightingDeduplicationService} private similarity,
 * for comparing two feature snapshots (assessment-level dedup).
 */
public final class CatFeatureSimilarity {

    private CatFeatureSimilarity() {
    }

    public static double similarity(CatFeatureSnapshot incoming, CatFeatureSnapshot candidate) {
        if (incoming == null || candidate == null) {
            return 0.0;
        }
        double coat = equalityScore(incoming.coatColor(), candidate.coatColor());
        double pattern = equalityScore(incoming.patternType(), candidate.patternType());
        double size = equalityScore(incoming.bodySize(), candidate.bodySize());
        double ear = booleanScore(incoming.earTipped(), candidate.earTipped());
        double features = overlapScore(incoming.specialFeatures(), candidate.specialFeatures());
        return 0.25 * coat + 0.20 * pattern + 0.15 * size + 0.30 * features + 0.10 * ear;
    }

    private static double equalityScore(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        return normalize(a).equals(normalize(b)) ? 1.0 : 0.0;
    }

    private static double booleanScore(boolean a, boolean b) {
        return a == b ? 1.0 : 0.0;
    }

    private static double overlapScore(List<String> a, List<String> b) {
        Set<String> left = normalizeSet(a);
        Set<String> right = normalizeSet(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String x : left) {
            if (right.contains(x)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static Set<String> normalizeSet(List<String> in) {
        if (in == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String x : in) {
            String n = normalize(x);
            if (!n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }

    private static String normalize(String x) {
        return x == null ? "" : x.trim().toLowerCase(Locale.ROOT);
    }
}
