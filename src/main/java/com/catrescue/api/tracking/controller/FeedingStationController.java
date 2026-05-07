package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.FeedingStationResponse;
import com.catrescue.api.tracking.dto.RemoteFeedResponse;
import com.catrescue.api.tracking.service.FeedingStationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/feeding-stations", "/feeding-stations"})
public class FeedingStationController {

    private final FeedingStationService feedingStationService;

    public FeedingStationController(FeedingStationService feedingStationService) {
        this.feedingStationService = feedingStationService;
    }

    @GetMapping
    public List<FeedingStationResponse> list() {
        return feedingStationService.listAll();
    }

    /**
     * Demo “remote feed”: updates {@code last_fed_at} only (no linkage to per-cat {@code feeding_records}).
     */
    @PostMapping("/{id}/remote-feed")
    public RemoteFeedResponse remoteFeed(@PathVariable("id") long id) {
        return feedingStationService.recordRemoteFeed(id);
    }
}
