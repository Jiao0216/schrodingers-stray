package com.catrescue.api.institution.dto;

public record ApproveInstitutionApplicationResponse(
        long organizationId,
        /** Returned once — store securely; cannot be retrieved later. */
        String apiToken,
        String apiPublicId
) {
}
