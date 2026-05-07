package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.CatHeatmapResponse;
import com.catrescue.api.tracking.dto.CatLastSeenResponse;
import com.catrescue.api.tracking.dto.CatProfileResponse;
import com.catrescue.api.tracking.dto.HeatmapPointDto;
import com.catrescue.api.tracking.dto.HeatmapSightingResponse;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.FeedingRecordEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.FeedingRecordJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.util.CoordinatePrecision;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-side helpers for cat movement history with time-decayed heatmap weights.
 */
@Service
public class CatHeatmapService {

    /** Sightings older than this window are excluded entirely from the heatmap. */
    public static final int HEATMAP_WINDOW_DAYS = 30;

    /** Heatmap marks sightings red when the cat has no feeding record within this many hours. */
    public static final int FEEDING_OVERDUE_HOURS = 24;

    private final CatJpaRepository catJpaRepository;
    private final SightingJpaRepository sightingJpaRepository;
    private final FeedingRecordJpaRepository feedingRecordJpaRepository;

    public CatHeatmapService(
            CatJpaRepository catJpaRepository,
            SightingJpaRepository sightingJpaRepository,
            FeedingRecordJpaRepository feedingRecordJpaRepository
    ) {
        this.catJpaRepository = catJpaRepository;
        this.sightingJpaRepository = sightingJpaRepository;
        this.feedingRecordJpaRepository = feedingRecordJpaRepository;
    }

    private Set<Long> catIdsFeedingOverdue() {
        Instant since = Instant.now().minus(FEEDING_OVERDUE_HOURS, ChronoUnit.HOURS);
        return new HashSet<>(feedingRecordJpaRepository.findCatIdsWithoutFeedingSince(since));
    }

    /**
     * Linear decay: weight 1.0 at {@code occurredAt == now}, weight 0.0 when {@code occurredAt} is 30+ days before {@code now}.
     * Values between use {@code 1 - ageDays/30}.
     */
    /**
     * Counts distinct cats (other than {@code excludeCatId}) with at least one sighting in the radius/time window.
     */
    public int countDistinctOtherCatsNear(double lat, double lng, Long excludeCatId, double radiusMeters, int windowDays) {
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<SightingEntity> rows = sightingJpaRepository.findCandidatesWithinRadiusAndTime(lat, lng, since, radiusMeters);
        Set<Long> ids = new HashSet<>();
        for (SightingEntity s : rows) {
            Long cid = s.getCatId();
            if (cid == null) {
                continue;
            }
            if (excludeCatId != null && excludeCatId.equals(cid)) {
                continue;
            }
            ids.add(cid);
        }
        return ids.size();
    }

    public static double linearDecayWeight(Instant occurredAt, Instant now) {
        if (occurredAt.isAfter(now)) {
            return 1.0;
        }
        double ageDays = Duration.between(occurredAt, now).toMillis() / 86_400_000.0;
        if (ageDays >= HEATMAP_WINDOW_DAYS) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (ageDays / HEATMAP_WINDOW_DAYS));
    }

    public CatHeatmapResponse buildHeatmap(Long catId) {
        Instant now = Instant.now();
        // Heatmap should be tolerant: if cat id does not exist yet, return an empty dataset
        // instead of HTTP 400 so frontend can render a graceful empty state.
        if (catJpaRepository.findById(catId).isEmpty()) {
            return new CatHeatmapResponse(catId, now, List.of());
        }
        Instant since = now.minus(HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
        List<SightingEntity> rows = sightingJpaRepository.findByCatIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                catId,
                since
        );
        List<HeatmapPointDto> points = new ArrayList<>();
        for (SightingEntity s : rows) {
            double w = linearDecayWeight(s.getOccurredAt(), now);
            if (w <= 0.0) {
                continue;
            }
            points.add(new HeatmapPointDto(s.getLatitude(), s.getLongitude(), w, s.getOccurredAt()));
        }
        return new CatHeatmapResponse(catId, now, points);
    }

    /**
     * Global heatmap across all cats/sightings in the time window.
     */
    public CatHeatmapResponse buildGlobalHeatmap() {
        Instant now = Instant.now();
        Instant since = now.minus(HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
        List<SightingEntity> rows = sightingJpaRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(since);
        List<HeatmapPointDto> points = new ArrayList<>();
        for (SightingEntity s : rows) {
            double w = linearDecayWeight(s.getOccurredAt(), now);
            if (w <= 0.0) {
                continue;
            }
            points.add(new HeatmapPointDto(s.getLatitude(), s.getLongitude(), w, s.getOccurredAt()));
        }
        return new CatHeatmapResponse(null, now, points);
    }

    /**
     * Returns recent heatmap points with image and feature metadata so frontend can render
     * photo popups and visually mark same-cat matches inferred by multimodal dedup flow.
     */
    public List<HeatmapSightingResponse> buildHeatmapSightings(Long catId) {
        Instant now = Instant.now();
        if (catJpaRepository.findById(catId).isEmpty()) {
            return List.of();
        }
        Instant since = now.minus(HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
        List<SightingEntity> rows = sightingJpaRepository.findByCatIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                catId,
                since
        );
        Set<Long> overdue = catIdsFeedingOverdue();
        boolean catOverdue = overdue.contains(catId);
        List<HeatmapSightingResponse> out = new ArrayList<>();
        for (SightingEntity s : rows) {
            double w = linearDecayWeight(s.getOccurredAt(), now);
            if (w <= 0.0) {
                continue;
            }
            boolean sameCat = s.getCatId() != null && s.getCatId().equals(catId);
            out.add(new HeatmapSightingResponse(
                    s.getId(),
                    s.getCatId(),
                    s.getLatitude(),
                    s.getLongitude(),
                    w,
                    s.getOccurredAt(),
                    s.getImageUrl(),
                    s.getCoatColor(),
                    s.getPatternType(),
                    s.getBodySize(),
                    s.isEarTipped(),
                    s.getSimilarityScore(),
                    s.getDedupStatus(),
                    sameCat,
                    catOverdue
            ));
        }
        return out;
    }

    /**
     * Global detailed heatmap points, regardless of cat id.
     */
    public List<HeatmapSightingResponse> buildGlobalHeatmapSightings() {
        Instant now = Instant.now();
        Instant since = now.minus(HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
        List<SightingEntity> rows = sightingJpaRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(since);
        Set<Long> overdue = catIdsFeedingOverdue();
        List<HeatmapSightingResponse> out = new ArrayList<>();
        for (SightingEntity s : rows) {
            double w = linearDecayWeight(s.getOccurredAt(), now);
            if (w <= 0.0) {
                continue;
            }
            Long cid = s.getCatId();
            boolean feedingOverdue = cid != null && overdue.contains(cid);
            out.add(new HeatmapSightingResponse(
                    s.getId(),
                    cid,
                    s.getLatitude(),
                    s.getLongitude(),
                    w,
                    s.getOccurredAt(),
                    s.getImageUrl(),
                    s.getCoatColor(),
                    s.getPatternType(),
                    s.getBodySize(),
                    s.isEarTipped(),
                    s.getSimilarityScore(),
                    s.getDedupStatus(),
                    cid != null,
                    feedingOverdue
            ));
        }
        return out;
    }

    /**
     * Header fields for the cat profile UI; sightings list comes from {@link #buildHeatmapSightings(Long)}.
     */
    public Optional<CatProfileResponse> buildProfile(Long catId) {
        return catJpaRepository.findById(catId).map(cat -> {
            Instant now = Instant.now();
            Instant since = now.minus(HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
            List<SightingEntity> rows = sightingJpaRepository.findByCatIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                    catId,
                    since
            );
            int count = 0;
            for (SightingEntity s : rows) {
                if (linearDecayWeight(s.getOccurredAt(), now) > 0.0) {
                    count++;
                }
            }
            List<String> spec = cat.getSpecialFeatures() == null ? List.of() : List.copyOf(cat.getSpecialFeatures());
            Instant lastFed = feedingRecordJpaRepository.findFirstByCatIdOrderByFedAtDesc(catId)
                    .map(FeedingRecordEntity::getFedAt)
                    .orElse(null);
            return new CatProfileResponse(
                    cat.getId(),
                    cat.getDisplayName(),
                    nz(cat.getCoatColor()),
                    nz(cat.getPatternType()),
                    nz(cat.getBodySize()),
                    cat.isEarTipped(),
                    cat.getSterilizationStatus() != null ? cat.getSterilizationStatus().name() : "UNKNOWN",
                    spec,
                    cat.getFirstSeenAt(),
                    cat.getLastSeenAt(),
                    cat.getLastSeenLat(),
                    cat.getLastSeenLng(),
                    count,
                    lastFed
            );
        });
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public CatLastSeenResponse buildLastSeen(Long catId) {
        CatEntity cat = catJpaRepository.findById(catId)
                .orElseThrow(() -> new IllegalArgumentException("Cat not found: " + catId));
        Optional<SightingEntity> latest = sightingJpaRepository.findFirstByCatIdOrderByOccurredAtDesc(catId);
        Long sightingId = latest.map(SightingEntity::getId).orElse(null);
        Long reporter = latest.map(SightingEntity::getReporterUserId).orElse(cat.getCreatedByUserId());
        String addr = latest.map(SightingEntity::getAddressText).orElse(null);
        return new CatLastSeenResponse(
                cat.getId(),
                cat.getLastSeenAt(),
                cat.getLastSeenLat(),
                cat.getLastSeenLng(),
                sightingId,
                reporter,
                addr
        );
    }

    private static final int PUBLIC_PROFILE_COORD_DECIMALS = 2;

    /**
     * True if the user reported a sighting for this cat or created the cat record.
     */
    public boolean userContributedToCat(Long catId, Long userId) {
        if (catId == null || userId == null || userId <= 0) {
            return false;
        }
        if (sightingJpaRepository.existsByCatIdAndReporterUserId(catId, userId)) {
            return true;
        }
        return catJpaRepository.findById(catId)
                .map(c -> userId.equals(c.getCreatedByUserId()))
                .orElse(false);
    }

    /**
     * Coarse profile for non-contributors (C-end): rounded location, no special-feature list, no last-fed time.
     */
    public Optional<CatProfileResponse> buildPublicProfile(Long catId) {
        return buildProfile(catId).map(p -> new CatProfileResponse(
                p.id(),
                p.displayName(),
                p.coatColor(),
                p.patternType(),
                p.bodySize(),
                p.earTipped(),
                p.sterilizationStatus(),
                List.of(),
                p.firstSeenAt(),
                p.lastSeenAt(),
                CoordinatePrecision.roundDegrees(p.lastSeenLatitude(), PUBLIC_PROFILE_COORD_DECIMALS),
                CoordinatePrecision.roundDegrees(p.lastSeenLongitude(), PUBLIC_PROFILE_COORD_DECIMALS),
                p.sightingsInWindowDays(),
                null
        ));
    }
}
