package com.catrescue.api.tracking.dto;

import java.time.Instant;

/**
 * Result of POST {@code .../feeding-stations/{id}/remote-feed}. {@code messageKey} is for UI i18n.
 */
public record RemoteFeedResponse(boolean ok, Instant lastFedAt, String messageKey) {
}
