package com.catrescue.api.service;

import com.catrescue.api.dto.PlatformStatsResponse;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import com.catrescue.api.tracking.domain.DedupStatus;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.FeedingStationDispenseLogJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.service.CatHeatmapService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class PlatformStatsService {

    private static final ZoneId PLATFORM_DAY_ZONE = ZoneId.of("America/Los_Angeles");

    private final AssessmentJpaRepository assessmentJpaRepository;
    private final SightingJpaRepository sightingJpaRepository;
    private final FeedingStationDispenseLogJpaRepository feedingStationDispenseLogJpaRepository;
    private final CatJpaRepository catJpaRepository;

    public PlatformStatsService(
            AssessmentJpaRepository assessmentJpaRepository,
            SightingJpaRepository sightingJpaRepository,
            FeedingStationDispenseLogJpaRepository feedingStationDispenseLogJpaRepository,
            CatJpaRepository catJpaRepository
    ) {
        this.assessmentJpaRepository = assessmentJpaRepository;
        this.sightingJpaRepository = sightingJpaRepository;
        this.feedingStationDispenseLogJpaRepository = feedingStationDispenseLogJpaRepository;
        this.catJpaRepository = catJpaRepository;
    }

    public PlatformStatsResponse summarize() {
        long totalAssessments = assessmentJpaRepository.count();
        long withGeo = assessmentJpaRepository.countWithLatitudeAndLongitudeNotNull();
        Instant since = Instant.now().minus(CatHeatmapService.HEATMAP_WINDOW_DAYS, ChronoUnit.DAYS);
        long sightings30 = sightingJpaRepository.countByOccurredAtGreaterThanEqual(since);
        long distinctCats = sightingJpaRepository.countDistinctCatIdWithNonNullCat();
        double ratio = totalAssessments <= 0 ? 0.0 : (double) withGeo / (double) totalAssessments;
        Instant startOfToday = ZonedDateTime.now(PLATFORM_DAY_ZONE).toLocalDate().atStartOfDay(PLATFORM_DAY_ZONE).toInstant();
        long todayDispenses = feedingStationDispenseLogJpaRepository.countScheduledSince(startOfToday);

        long totalCatProfiles = catJpaRepository.count();
        long totalSightings = sightingJpaRepository.count();
        long distinctDupCats =
                sightingJpaRepository.countDistinctCatIdByDedupStatus(DedupStatus.CONFIRMED_DUPLICATE);
        long catsPendingTnr = catJpaRepository.countBySterilizationStatusIn(
                List.of(SterilizationStatus.UNKNOWN, SterilizationStatus.NOT_STERILIZED));

        return new PlatformStatsResponse(
                totalAssessments,
                sightings30,
                distinctCats,
                withGeo,
                Math.round(ratio * 1000.0) / 1000.0,
                todayDispenses,
                totalCatProfiles,
                totalSightings,
                distinctDupCats,
                catsPendingTnr
        );
    }
}
