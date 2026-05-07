package com.catrescue.api.institution.dto;

import com.catrescue.api.institution.domain.InstitutionApplicationStatus;
import com.catrescue.api.institution.domain.InstitutionOrgType;

import java.time.Instant;

public record InstitutionApplicationViewDto(
        long id,
        String organizationName,
        String contactEmail,
        String contactName,
        InstitutionOrgType orgType,
        String missionNote,
        Double proposedMinLat,
        Double proposedMaxLat,
        Double proposedMinLng,
        Double proposedMaxLng,
        InstitutionApplicationStatus status,
        String adminNote,
        Long approvedOrganizationId,
        Instant createdAt
) {
}
