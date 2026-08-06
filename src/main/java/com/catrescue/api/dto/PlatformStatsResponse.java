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
        long todayScheduledStationDispenses,
        /** Rows in {@code cats} (archived strays on the platform). */
        long totalCatProfiles,
        /** All-time sighting rows (community records). */
        long totalSightings,
        /**
         * Distinct archive cats with ≥1 sighting in {@code CONFIRMED_DUPLICATE} status (merged as same cat;
         * each {@code cat_id} counted at most once).
         */
        long distinctCatsDuplicateIdentified,
        /** Cats with sterilization {@code UNKNOWN} or {@code NOT_STERILIZED} (TNR pipeline). */
        long catsPendingTnr
) {
}
