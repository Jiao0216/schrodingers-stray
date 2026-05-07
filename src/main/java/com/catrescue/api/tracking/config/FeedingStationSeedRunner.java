package com.catrescue.api.tracking.config;

import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import com.catrescue.api.tracking.repository.FeedingStationJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Inserts a handful of Bay Area demo feeding stations when the table is empty.
 */
@Component
@Order(50)
public class FeedingStationSeedRunner implements ApplicationRunner {

    private final FeedingStationJpaRepository feedingStationJpaRepository;

    public FeedingStationSeedRunner(FeedingStationJpaRepository feedingStationJpaRepository) {
        this.feedingStationJpaRepository = feedingStationJpaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (feedingStationJpaRepository.count() > 0) {
            return;
        }
        Instant base = Instant.now().minus(6, ChronoUnit.HOURS);
        List<FeedingStationEntity> rows = List.of(
                station(
                        "SoFA Community Bowl\n350 S 1st St, San Jose, CA 95113",
                        37.3298,
                        -121.8865,
                        base.minus(2, ChronoUnit.HOURS)),
                station(
                        "Santa Clara Civic Pantry\n1500 Warburton Ave, Santa Clara, CA 95050",
                        37.3544,
                        -121.9552,
                        base.minus(30, ChronoUnit.MINUTES)),
                station(
                        "Castro Street Care Station\n200 Castro St, Mountain View, CA 94041",
                        37.3893,
                        -122.0819,
                        base.minus(1, ChronoUnit.DAYS)),
                station(
                        "Lake Elizabeth Feeder\n40000 Paseo Padre Pkwy, Fremont, CA 94538",
                        37.5489,
                        -121.9630,
                        base.minus(4, ChronoUnit.HOURS)),
                station(
                        "California Ave Stray Station\n200 California Ave, Palo Alto, CA 94306",
                        37.4292,
                        -122.1458,
                        base.minus(20, ChronoUnit.MINUTES)));
        feedingStationJpaRepository.saveAll(rows);
    }

    private static FeedingStationEntity station(String nameWithAddress, double lat, double lng, Instant lastFed) {
        FeedingStationEntity e = new FeedingStationEntity();
        e.setName(nameWithAddress);
        e.setLatitude(lat);
        e.setLongitude(lng);
        e.setLastFedAt(lastFed);
        return e;
    }
}
