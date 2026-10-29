package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.service.SightingDeduplicationService.SeenAt;
import com.catrescue.api.tracking.vector.VectorMatchResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SameCatGatingTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    // San Jose City Hall
    private static final double LAT = 37.3382;
    private static final double LNG = -121.8863;

    private static VectorMatchResult match(long sightingId, double distance) {
        return new VectorMatchResult("sighting_" + sightingId, sightingId, null, distance, "", "", "");
    }

    @Test
    void skipsLookAlikeFarAwayAndKeepsNearbyCandidate() {
        Map<Long, SeenAt> db = Map.of(
                1L, new SeenAt(37.7749, -122.4194, NOW.minus(Duration.ofDays(2))),   // San Francisco, ~70 km
                2L, new SeenAt(37.3400, -121.8900, NOW.minus(Duration.ofDays(3)))    // ~400 m away
        );
        Optional<VectorMatchResult> best = SightingDeduplicationService.selectGatedMatch(
                List.of(match(1, 0.05), match(2, 0.20)),
                id -> Optional.ofNullable(db.get(id)),
                LAT, LNG, NOW, 3000, 180
        );
        assertThat(best).map(VectorMatchResult::sightingId).contains(2L);
    }

    @Test
    void skipsCandidatesOutsideTimeWindow() {
        Map<Long, SeenAt> db = Map.of(1L, new SeenAt(LAT, LNG, NOW.minus(Duration.ofDays(400))));
        assertThat(SightingDeduplicationService.selectGatedMatch(
                List.of(match(1, 0.05)), id -> Optional.ofNullable(db.get(id)), LAT, LNG, NOW, 3000, 180
        )).isEmpty();
    }

    @Test
    void skipsStaleVectorsWhoseSightingIsGone() {
        assertThat(SightingDeduplicationService.selectGatedMatch(
                List.of(match(9, 0.01)), id -> Optional.empty(), LAT, LNG, NOW, 3000, 180
        )).isEmpty();
    }

    @Test
    void unknownLocationIsNotFilteredOut() {
        Map<Long, SeenAt> db = Map.of(1L, new SeenAt(null, null, NOW));
        assertThat(SightingDeduplicationService.selectGatedMatch(
                List.of(match(1, 0.1)), id -> Optional.ofNullable(db.get(id)), LAT, LNG, NOW, 3000, 180
        )).isPresent();
    }

    @Test
    void featureTagsAreNormalizedToVocabulary() {
        assertThat(OpenAiCatFeatureExtractionService.normalizeFeatureTag(" White Paws ")).isEqualTo("white_paws");
        assertThat(OpenAiCatFeatureExtractionService.normalizeFeatureTag("ear-tip-tnr")).isEqualTo("ear_tip_tnr");
        assertThat(OpenAiCatFeatureExtractionService.FEATURE_TAGS).contains("white_paws", "ear_tip_tnr");
        assertThat(OpenAiCatFeatureExtractionService.FEATURE_TAGS).doesNotContain("cute");
    }
}
