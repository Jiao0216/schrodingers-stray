package com.catrescue.api.tracking.dto;

import java.time.Instant;
import java.util.List;

public record CatHeatmapResponse(
        Long catId,
        Instant generatedAt,
        List<HeatmapPointDto> points
) {
}
