package com.catrescue.api.dto;

/**
 * Database-backed dashboard totals (not limited to list API page size).
 */
public record PlatformStatsResponse(
        /** All community assessment rows ever recorded. */
        long totalAssessments,
        /** Sighting rows with {@code occurredAt} in the last 30 days. */
        long sightingsLast30Days,
        /** Distinct {@code cat_id} in sightings (excludes null); same cat reported many times counts once. */
        long distinctCatsWithSightings,
        /** Assessments with both latitude and longitude set. */
        long assessmentsWithGeo,
        /** {@code assessmentsWithGeo} / {@code totalAssessments}, or 0 if total is 0. */
        double assessmentWithGeoRatio,
        /** Scheduled smart-feeder dispenses since local (America/Los_Angeles) midnight today. */
        long todayScheduledStationDispenses
) {
}
