package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.persistence.FeedingStationDispenseLogEntity;
import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import com.catrescue.api.tracking.repository.FeedingStationDispenseLogJpaRepository;
import com.catrescue.api.tracking.repository.FeedingStationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Runs smart-feeder “dispenses” twice per day (default 08:00 and 18:00 America/Los_Angeles),
 * updates {@link FeedingStationEntity#getLastFedAt()} and appends scheduled dispense logs (stand-in for per-cat feeding check-ins).
 */
@Service
public class FeedingStationScheduledDispenseService {

    private static final Logger log = LoggerFactory.getLogger(FeedingStationScheduledDispenseService.class);

    private final FeedingStationJpaRepository feedingStationJpaRepository;
    private final FeedingStationDispenseLogJpaRepository feedingStationDispenseLogJpaRepository;

    public FeedingStationScheduledDispenseService(
            FeedingStationJpaRepository feedingStationJpaRepository,
            FeedingStationDispenseLogJpaRepository feedingStationDispenseLogJpaRepository
    ) {
        this.feedingStationJpaRepository = feedingStationJpaRepository;
        this.feedingStationDispenseLogJpaRepository = feedingStationDispenseLogJpaRepository;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Los_Angeles")
    public void morningDispense() {
        runScheduledSlot("08:00");
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "America/Los_Angeles")
    public void eveningDispense() {
        runScheduledSlot("18:00");
    }

    @Transactional
    public void runScheduledSlot(String slotLabel) {
        Instant now = Instant.now();
        var stations = feedingStationJpaRepository.findAll();
        if (stations.isEmpty()) {
            return;
        }
        for (FeedingStationEntity s : stations) {
            s.setLastFedAt(now);
            feedingStationJpaRepository.save(s);
            FeedingStationDispenseLogEntity row = new FeedingStationDispenseLogEntity();
            row.setFeedingStationId(s.getId());
            row.setDispensedAt(now);
            row.setScheduled(true);
            row.setSlot(slotLabel);
            feedingStationDispenseLogJpaRepository.save(row);
        }
        log.info("Scheduled feeder dispense {} for {} stations", slotLabel, stations.size());
    }
}
