package com.catrescue.api.institution.dto;

public record PatchFeedingStationRequest(
        /** When true, assigns this station to the calling organization. */
        Boolean assignToMyOrganization,
        String partnerNotes
) {
}
