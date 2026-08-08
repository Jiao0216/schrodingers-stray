-- Community feeding spots (bowl locations) and check-in records.
-- Hibernate ddl-auto=update also creates these; kept for reference / manual installs.

CREATE TABLE IF NOT EXISTS feeding_spots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    photo_bytes LONGBLOB NOT NULL,
    photo_content_type VARCHAR(120) NULL,
    description VARCHAR(500) NULL,
    created_by_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_feeding_spot_active (active),
    INDEX idx_feeding_spot_created (created_at)
);

CREATE TABLE IF NOT EXISTS feeding_spot_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feeding_spot_id BIGINT NOT NULL,
    fed_by_user_id BIGINT NULL,
    fed_at TIMESTAMP(6) NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_fsr_spot_fed (feeding_spot_id, fed_at)
);

CREATE TABLE IF NOT EXISTS shelter_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(300) NOT NULL,
    address VARCHAR(500) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    phone VARCHAR(80) NULL,
    services VARCHAR(500) NOT NULL,
    notes VARCHAR(2000) NULL,
    INDEX idx_shelter_info_lat_lng (latitude, longitude)
);
