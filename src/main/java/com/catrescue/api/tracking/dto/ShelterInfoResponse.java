package com.catrescue.api.tracking.dto;

public record ShelterInfoResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        String phone,
        java.util.List<String> services,
        String notes,
        Double distanceMeters
) {
}
