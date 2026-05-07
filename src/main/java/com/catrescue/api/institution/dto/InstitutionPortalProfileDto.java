package com.catrescue.api.institution.dto;

import com.catrescue.api.institution.domain.InstitutionOrgType;
import com.catrescue.api.institution.domain.InstitutionSubscriptionTier;

import java.time.Instant;

public record InstitutionPortalProfileDto(
        long id,
        String name,
        InstitutionOrgType orgType,
        InstitutionSubscriptionTier subscriptionTier,
        Instant subscriptionExpiresAt,
        double territoryMinLat,
        double territoryMaxLat,
        double territoryMinLng,
        double territoryMaxLng
) {
}
