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
 * Inserts demo feeding stations near Milpitas / North SJ when the table is empty.
 * To pick up new demo coordinates in dev, truncate {@code feeding_stations} and restart.
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
        // Demo coords clustered near Milpitas / North SJ so "nearest feeder" matches ~1km cat lookup radius.
        List<FeedingStationEntity> rows = List.of(
                station(
                        "Milpitas Civic Care Bowl\n455 E Calaveras Blvd, Milpitas, CA 95035",
                        37.4289,
                        -121.8958,
                        base.minus(45, ChronoUnit.MINUTES)),
                station(
                        "Great Mall Community Feeder\n447 Great Mall Dr, Milpitas, CA 95035",
                        37.4179,
                        -121.8981,
                        base.minus(2, ChronoUnit.HOURS)),
                station(
                        "Landess Ave Stray Station\n1770 Landess Ave, Milpitas, CA 95035",
                        37.4321,
                        -121.8874,
                        base.minus(20, ChronoUnit.MINUTES)),
                station(
                        "Berryessa Creek Bowl\n3180 Berryessa Rd, San Jose, CA 95132",
                        37.3902,
                        -121.8578,
                        base.minus(3, ChronoUnit.HOURS)),
                station(
                        "Warm Springs Care Point\n46585 Mission Blvd, Fremont, CA 94539",
                        37.4852,
                        -121.9294,
                        base.minus(1, ChronoUnit.DAYS)));
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
