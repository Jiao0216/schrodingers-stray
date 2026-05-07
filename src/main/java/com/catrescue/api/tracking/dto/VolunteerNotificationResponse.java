package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.NotificationType;

import java.time.Instant;

public record VolunteerNotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        String payloadJson,
        boolean acknowledged,
        Long relatedCatId,
        Long relatedSightingId,
        Instant createdAt
) {
}
