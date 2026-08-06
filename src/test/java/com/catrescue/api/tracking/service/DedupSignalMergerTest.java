package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.ClipDedupDecision;
import com.catrescue.api.tracking.dto.DedupDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedupSignalMergerTest {

    @Test
    void merge_eitherSignalPendingReview() {
        DedupDecision featureMiss = new DedupDecision(false, 0.5, null, null, "feature miss");
        ClipDedupDecision clipHit = new ClipDedupDecision(true, 0.12, 9L, 99L, "clip hit");

        DedupSignalMerger.CombinedDedupDecision out = DedupSignalMerger.merge(featureMiss, clipHit);
        assertTrue(out.pendingReview());
        assertEquals(99L, out.suggestedCatId());
        assertEquals(9L, out.duplicateOfSightingId());
    }

    @Test
    void merge_featurePreferredWhenBothHit() {
        DedupDecision featureHit = new DedupDecision(true, 0.85, 1L, 10L, "feature hit");
        ClipDedupDecision clipHit = new ClipDedupDecision(true, 0.05, 2L, 20L, "clip hit");

        DedupSignalMerger.CombinedDedupDecision out = DedupSignalMerger.merge(featureHit, clipHit);
        assertTrue(out.pendingReview());
        assertEquals(10L, out.suggestedCatId());
        assertEquals(1L, out.duplicateOfSightingId());
    }

    @Test
    void merge_neitherHit_notPending() {
        DedupDecision featureMiss = new DedupDecision(false, 0.4, null, null, "feature miss");
        ClipDedupDecision clipMiss = new ClipDedupDecision(false, 0.8, 3L, 30L, "clip miss");

        DedupSignalMerger.CombinedDedupDecision out = DedupSignalMerger.merge(featureMiss, clipMiss);
        assertFalse(out.pendingReview());
    }
}
