package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * Body for {@code POST .../feeding-records}. All fields optional; {@code fedAt} defaults to now.
 */
public record CreateFeedingRecordRequest(Long reporterUserId, String notes, Instant fedAt) {
}
