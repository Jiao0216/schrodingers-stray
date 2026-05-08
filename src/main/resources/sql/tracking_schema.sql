-- Stray cat tracking schema for MySQL 8+
-- Tables: users, cats, sightings

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  phone VARCHAR(40) NULL,
  password_hash VARCHAR(120) NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'VOLUNTEER',
  service_latitude DOUBLE NULL,
  service_longitude DOUBLE NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  display_name VARCHAR(100) NULL,
  coat_color VARCHAR(40) NULL,
  pattern_type VARCHAR(40) NULL,
  body_size VARCHAR(20) NULL,
  special_features TEXT NULL,
  sterilization_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
  ear_tipped BOOLEAN NOT NULL DEFAULT FALSE,
  sterilization_confidence DOUBLE NULL,
  first_seen_at TIMESTAMP(3) NOT NULL,
  last_seen_at TIMESTAMP(3) NOT NULL,
  last_seen_lat DOUBLE NOT NULL,
  last_seen_lng DOUBLE NOT NULL,
  created_by_user_id BIGINT NULL,
  absence_reminder_sent_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_cats_last_seen (last_seen_at),
  INDEX idx_cats_last_coords (last_seen_lat, last_seen_lng),
  INDEX idx_cats_sterilized (sterilization_status, ear_tipped)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sightings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cat_id BIGINT NULL,
  reporter_user_id BIGINT NOT NULL,
  image_url MEDIUMTEXT NOT NULL,
  address_text VARCHAR(600) NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  coat_color VARCHAR(40) NOT NULL,
  pattern_type VARCHAR(40) NOT NULL,
  body_size VARCHAR(20) NOT NULL,
  special_features TEXT NULL,
  ear_tipped BOOLEAN NOT NULL DEFAULT FALSE,
  ear_tipped_confidence DOUBLE NOT NULL DEFAULT 0,
  feature_extraction_confidence DOUBLE NOT NULL DEFAULT 0,
  dedup_status VARCHAR(30) NOT NULL,
  suggested_cat_id BIGINT NULL,
  duplicate_of_sighting_id BIGINT NULL,
  similarity_score DOUBLE NULL,
  dedup_reason TEXT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_sightings_time_coords (occurred_at, latitude, longitude),
  INDEX idx_sightings_cat (cat_id),
  INDEX idx_sightings_dedup (dedup_status, suggested_cat_id, occurred_at),
  INDEX idx_sightings_reporter (reporter_user_id, occurred_at),
  CONSTRAINT fk_sighting_cat FOREIGN KEY (cat_id) REFERENCES cats(id),
  CONSTRAINT fk_sighting_suggested_cat FOREIGN KEY (suggested_cat_id) REFERENCES cats(id),
  CONSTRAINT fk_sighting_duplicate_ref FOREIGN KEY (duplicate_of_sighting_id) REFERENCES sightings(id),
  CONSTRAINT fk_sighting_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS volunteer_notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  volunteer_user_id BIGINT NOT NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  payload_json TEXT NULL,
  acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
  related_cat_id BIGINT NULL,
  related_sighting_id BIGINT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_vol_notif_user_time (volunteer_user_id, created_at),
  INDEX idx_vol_notif_sighting (related_sighting_id),
  CONSTRAINT fk_vol_notif_user FOREIGN KEY (volunteer_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- If upgrading an older database, run:
-- ALTER TABLE users ADD COLUMN service_latitude DOUBLE NULL;
-- ALTER TABLE users ADD COLUMN service_longitude DOUBLE NULL;
-- ALTER TABLE cats ADD COLUMN absence_reminder_sent_at TIMESTAMP(3) NULL;
-- ALTER TABLE users ADD COLUMN bio TEXT NULL;
-- ALTER TABLE users ADD COLUMN volunteer_points BIGINT NOT NULL DEFAULT 0;
-- ALTER TABLE users ADD COLUMN notify_nearby_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS volunteer_feeding_checkins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  feeding_station_id BIGINT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  note VARCHAR(500) NULL,
  points_awarded INT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_vol_feed_user_time (user_id, created_at),
  INDEX idx_vol_feed_station (feeding_station_id),
  CONSTRAINT fk_vol_feed_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS volunteer_badges_earned (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  badge_code VARCHAR(40) NOT NULL,
  earned_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_vol_badge_user_code (user_id, badge_code),
  INDEX idx_vol_badge_user (user_id),
  CONSTRAINT fk_vol_badge_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS volunteer_saved_cats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  cat_id BIGINT NOT NULL,
  snapshot_json LONGTEXT NOT NULL,
  saved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_vol_saved_user_cat (user_id, cat_id),
  INDEX idx_vol_saved_user_time (user_id, saved_at),
  CONSTRAINT fk_vol_saved_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
