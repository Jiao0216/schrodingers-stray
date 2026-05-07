package com.catrescue.api.tracking.dto;

/**
 * Flat heatmap sample for Leaflet.heat.
 */
public record HeatmapSampleResponse(
        double lat,
        double lng,
        double weight
) {
}
