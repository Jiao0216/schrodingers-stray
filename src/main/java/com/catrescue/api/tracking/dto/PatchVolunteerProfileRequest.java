package com.catrescue.api.tracking.dto;

import jakarta.validation.constraints.Size;

public record PatchVolunteerProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 40) String phone,
        @Size(max = 2000) String bio,
        Double serviceLatitude,
        Double serviceLongitude,
        Boolean notifyNearbyEnabled
) {
}
