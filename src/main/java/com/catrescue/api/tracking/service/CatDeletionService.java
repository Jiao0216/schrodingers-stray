package com.catrescue.api.tracking.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Removes one cat and all tracking rows that belong to it (sightings merged into this archive,
 * pending duplicate suggestions for this cat, feeding records, saved snapshots, related notifications).
 * Does not delete volunteer feeding check-ins or assessments (no direct cat FK).
 */
@Service
public class CatDeletionService {

    private final JdbcTemplate jdbcTemplate;

    public CatDeletionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Optional<Map<String, Object>> deleteCat(long catId) {
        if (catId <= 0) {
            return Optional.empty();
        }
        Integer exists = jdbcTemplate.query(
                "SELECT 1 FROM cats WHERE id = ? LIMIT 1",
                rs -> rs.next() ? 1 : null,
                catId
        );
        if (exists == null) {
            return Optional.empty();
        }

        Map<String, Integer> deletedRows = new LinkedHashMap<>();

        jdbcTemplate.update("UPDATE sightings SET suggested_cat_id = NULL WHERE suggested_cat_id = ? AND cat_id IS NOT NULL", catId);

        List<Long> sightingIds = jdbcTemplate.query(
                "SELECT id FROM sightings WHERE cat_id = ? OR (cat_id IS NULL AND suggested_cat_id = ?)",
                (rs, rowNum) -> rs.getLong(1),
                catId,
                catId
        );

        if (!sightingIds.isEmpty()) {
            String inList = joinIds(sightingIds);
            jdbcTemplate.update("UPDATE sightings SET duplicate_of_sighting_id = NULL WHERE duplicate_of_sighting_id IN (" + inList + ")");
            deletedRows.put("tracking_sightings", deleteTrackingSightings(inList));
            deletedRows.put(
                    "volunteer_notifications",
                    jdbcTemplate.update(
                            "DELETE FROM volunteer_notifications WHERE related_cat_id = ? OR related_sighting_id IN (" + inList + ")",
                            catId
                    )
            );
            deletedRows.put("sightings", jdbcTemplate.update("DELETE FROM sightings WHERE id IN (" + inList + ")"));
        } else {
            deletedRows.put("tracking_sightings", 0);
            deletedRows.put("volunteer_notifications", jdbcTemplate.update("DELETE FROM volunteer_notifications WHERE related_cat_id = ?", catId));
            deletedRows.put("sightings", 0);
        }

        deletedRows.put("feeding_records", jdbcTemplate.update("DELETE FROM feeding_records WHERE cat_id = ?", catId));
        deletedRows.put("volunteer_saved_cats", jdbcTemplate.update("DELETE FROM volunteer_saved_cats WHERE cat_id = ?", catId));
        deletedRows.put("cats", jdbcTemplate.update("DELETE FROM cats WHERE id = ?", catId));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("catId", catId);
        body.put("deletedRows", deletedRows);
        return Optional.of(body);
    }

    private int deleteTrackingSightings(String inList) {
        try {
            return jdbcTemplate.update("DELETE FROM tracking_sightings WHERE sighting_id IN (" + inList + ")");
        } catch (Exception ignored) {
            try {
                return jdbcTemplate.update("DELETE FROM tracking_sightings WHERE sightingId IN (" + inList + ")");
            } catch (Exception ignored2) {
                return 0;
            }
        }
    }

    private static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
