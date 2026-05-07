package com.catrescue.api.institution.dto;

import com.catrescue.api.institution.domain.InstitutionSubscriptionTier;

import java.time.Instant;

/**
 * Optional overrides when approving an application (otherwise bbox comes from the application).
 */
public record ApproveInstitutionApplicationRequest(
        Double territoryMinLat,
        Double territoryMaxLat,
        Double territoryMinLng,
        Double territoryMaxLng,
        InstitutionSubscriptionTier subscriptionTier,
        Instant subscriptionExpiresAt,
        String adminNote
) {
}
