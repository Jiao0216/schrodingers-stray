-- Seed data for heatmap troubleshooting.
-- City cluster: San Francisco downtown area.
-- Usage:
--   mysql -u root -p cat_rescue < src/main/resources/sql/seed_heatmap_test_data.sql

INSERT INTO users (id, email, display_name, phone, role, service_latitude, service_longitude, created_at)
VALUES
  (90001, 'heatmap-reporter@example.com', 'Heatmap Reporter', NULL, 'VOLUNTEER', 37.7749, -122.4194, NOW(3))
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  service_latitude = VALUES(service_latitude),
  service_longitude = VALUES(service_longitude);

INSERT INTO cats (
  id, display_name, coat_color, pattern_type, body_size, special_features,
  sterilization_status, ear_tipped, sterilization_confidence, first_seen_at, last_seen_at,
  last_seen_lat, last_seen_lng, created_by_user_id, absence_reminder_sent_at, created_at, updated_at
)
VALUES (
  90001, 'Heatmap-Test-Cat', 'gray', 'tabby', 'medium', '["left ear nick"]',
  'UNKNOWN', 0, 0.20, NOW(3) - INTERVAL 5 DAY, NOW(3),
  37.7749, -122.4194, 90001, NULL, NOW(3), NOW(3)
)
ON DUPLICATE KEY UPDATE
  last_seen_at = VALUES(last_seen_at),
  last_seen_lat = VALUES(last_seen_lat),
  last_seen_lng = VALUES(last_seen_lng),
  updated_at = NOW(3);

INSERT INTO sightings (
  id, cat_id, reporter_user_id, image_url, address_text, latitude, longitude, occurred_at,
  coat_color, pattern_type, body_size, special_features, ear_tipped, ear_tipped_confidence,
  feature_extraction_confidence, dedup_status, suggested_cat_id, duplicate_of_sighting_id,
  similarity_score, dedup_reason, created_at, updated_at
)
VALUES
  (91001, 90001, 90001, 'https://example.com/cat-1.jpg', 'Market St, San Francisco', 37.7749, -122.4194, NOW(3) - INTERVAL 1 DAY,
   'gray', 'tabby', 'medium', '["left ear nick"]', 0, 0.10, 0.92, 'NEW_CONFIRMED', NULL, NULL, NULL, 'seed', NOW(3), NOW(3)),
  (91002, 90001, 90001, 'https://example.com/cat-2.jpg', 'Mission St, San Francisco', 37.7752, -122.4187, NOW(3) - INTERVAL 3 DAY,
   'gray', 'tabby', 'medium', '["left ear nick"]', 0, 0.10, 0.92, 'NEW_CONFIRMED', NULL, NULL, NULL, 'seed', NOW(3), NOW(3)),
  (91003, 90001, 90001, 'https://example.com/cat-3.jpg', 'Howard St, San Francisco', 37.7743, -122.4179, NOW(3) - INTERVAL 7 DAY,
   'gray', 'tabby', 'medium', '["left ear nick"]', 0, 0.10, 0.92, 'NEW_CONFIRMED', NULL, NULL, NULL, 'seed', NOW(3), NOW(3)),
  (91004, 90001, 90001, 'https://example.com/cat-4.jpg', 'Folsom St, San Francisco', 37.7760, -122.4203, NOW(3) - INTERVAL 12 DAY,
   'gray', 'tabby', 'medium', '["left ear nick"]', 0, 0.10, 0.92, 'NEW_CONFIRMED', NULL, NULL, NULL, 'seed', NOW(3), NOW(3)),
  (91005, 90001, 90001, 'https://example.com/cat-5.jpg', 'Van Ness Ave, San Francisco', 37.7737, -122.4212, NOW(3) - INTERVAL 20 DAY,
   'gray', 'tabby', 'medium', '["left ear nick"]', 0, 0.10, 0.92, 'NEW_CONFIRMED', NULL, NULL, NULL, 'seed', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  occurred_at = VALUES(occurred_at),
  latitude = VALUES(latitude),
  longitude = VALUES(longitude),
  updated_at = NOW(3);
