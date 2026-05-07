package com.catrescue.api.admin.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Development-only convenience endpoint for quickly resetting tracking data.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/admin")
public class AdminResetController {

    private final JdbcTemplate jdbcTemplate;

    public AdminResetController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAllData() {
        Map<String, Integer> deletedRows = new LinkedHashMap<>();
        // Order matters because sightings references cats and itself.
        deletedRows.put("tracking_sightings", safeDelete("DELETE FROM tracking_sightings"));
        deletedRows.put("sightings", safeDelete("DELETE FROM sightings"));
        deletedRows.put("feeding_records", safeDelete("DELETE FROM feeding_records"));
        deletedRows.put("volunteer_points", safeDelete("DELETE FROM volunteer_points"));
        deletedRows.put("volunteer_feeding_checkins", safeDelete("DELETE FROM volunteer_feeding_checkins"));
        deletedRows.put("volunteer_notifications", safeDelete("DELETE FROM volunteer_notifications"));
        deletedRows.put("notifications", safeDelete("DELETE FROM notifications"));
        deletedRows.put("cats", safeDelete("DELETE FROM cats"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "reset completed");
        body.put("deletedRows", deletedRows);
        return ResponseEntity.ok(body);
    }

    @SuppressWarnings("null")
    private int safeDelete(String sql) {
        try {
            return jdbcTemplate.update(sql);
        } catch (Exception ignored) {
            // Some optional tables may not exist in all environments.
            return 0;
        }
    }
}

