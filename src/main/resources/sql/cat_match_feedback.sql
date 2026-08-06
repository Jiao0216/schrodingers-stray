-- Volunteer feedback for same-cat dedup calibration (CLIP + OpenAI Vision scores).

CREATE TABLE IF NOT EXISTS cat_match_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sighting_id BIGINT NOT NULL,
    matched_sighting_id BIGINT NULL,
    matched_cat_id BIGINT NULL,
    clip_distance FLOAT NULL,
    feature_similarity FLOAT NULL,
    volunteer_confirmed BOOLEAN NULL,
    confirmed_at TIMESTAMP(3) NULL,
    confirmed_by VARCHAR(120) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_cmf_sighting (sighting_id),
    INDEX idx_cmf_confirmed (volunteer_confirmed, confirmed_at),
    CONSTRAINT fk_cmf_sighting FOREIGN KEY (sighting_id) REFERENCES sightings(id)
) ENGINE=InnoDB;
