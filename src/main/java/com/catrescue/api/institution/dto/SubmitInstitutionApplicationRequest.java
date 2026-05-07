package com.catrescue.api.institution.dto;

import com.catrescue.api.institution.domain.InstitutionOrgType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitInstitutionApplicationRequest(
        @NotBlank String organizationName,
        @NotBlank @Email String contactEmail,
        String contactName,
        @NotNull InstitutionOrgType orgType,
        String missionNote,
        Double proposedMinLat,
        Double proposedMaxLat,
        Double proposedMinLng,
        Double proposedMaxLng
) {
}
