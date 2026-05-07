package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.FeedingStationResponse;
import com.catrescue.api.tracking.dto.RemoteFeedResponse;
import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import com.catrescue.api.tracking.repository.FeedingStationJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class FeedingStationService {

    private final FeedingStationJpaRepository feedingStationJpaRepository;

    public FeedingStationService(FeedingStationJpaRepository feedingStationJpaRepository) {
        this.feedingStationJpaRepository = feedingStationJpaRepository;
    }

    public List<FeedingStationResponse> listAll() {
        return feedingStationJpaRepository.findAll().stream().map(FeedingStationService::toDto).toList();
    }

    @Transactional
    public RemoteFeedResponse recordRemoteFeed(long stationId) {
        FeedingStationEntity row = feedingStationJpaRepository.findById(stationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feeding station"));
        Instant now = Instant.now();
        row.setLastFedAt(now);
        feedingStationJpaRepository.save(row);
        return new RemoteFeedResponse(true, now, "feedingStationRemoteFeedOk");
    }

    private static FeedingStationResponse toDto(FeedingStationEntity e) {
        String raw = e.getName() == null ? "" : e.getName();
        int nl = raw.indexOf('\n');
        String title;
        String addr;
        if (nl < 0) {
            title = raw.trim();
            addr = "";
        } else {
            title = raw.substring(0, nl).trim();
            addr = raw.substring(nl + 1).trim();
        }
        return new FeedingStationResponse(
                e.getId(),
                title,
                addr,
                e.getLatitude(),
                e.getLongitude(),
                e.getLastFedAt()
        );
    }
}
