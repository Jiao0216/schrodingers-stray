package com.catrescue.api.controller;

import com.catrescue.api.dto.PlatformStatsResponse;
import com.catrescue.api.service.PlatformStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/stats", "/stats"})
public class PlatformStatsController {

    private final PlatformStatsService platformStatsService;

    public PlatformStatsController(PlatformStatsService platformStatsService) {
        this.platformStatsService = platformStatsService;
    }

    /** Dashboard KPIs: full-table counts, not capped list lengths. */
    @GetMapping("/platform")
    public PlatformStatsResponse platform() {
        return platformStatsService.summarize();
    }
}
