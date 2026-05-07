package com.catrescue.api.tracking.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatHeatmapServiceTest {

    @Test
    void linearDecay_todayIsFullWeight() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        double w = CatHeatmapService.linearDecayWeight(now, now);
        assertEquals(1.0, w, 1e-9);
    }

    @Test
    void linearDecay_thirtyDaysAgoIsZeroAndExcludedBand() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        assertEquals(0.0, CatHeatmapService.linearDecayWeight(thirtyDaysAgo, now), 1e-9);
        Instant older = now.minus(31, ChronoUnit.DAYS);
        assertEquals(0.0, CatHeatmapService.linearDecayWeight(older, now), 1e-9);
    }

    @Test
    void linearDecay_halfwayIsHalfWeight() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        Instant t = now.minus(15, ChronoUnit.DAYS);
        double w = CatHeatmapService.linearDecayWeight(t, now);
        assertEquals(0.5, w, 1e-6);
    }

    @Test
    void linearDecay_futureSightingClampedToOne() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        Instant future = now.plus(1, ChronoUnit.DAYS);
        assertTrue(CatHeatmapService.linearDecayWeight(future, now) <= 1.0);
    }
}
