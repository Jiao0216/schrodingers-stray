package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record OverdueCatResponse(long id, String displayName, Instant lastFedAt) {
}
