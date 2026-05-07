package com.catrescue.api.dto;

public record TnrLocationDto(
        String id,
        String name,
        String shortDescription,
        String city,
        String streetAddress,
        String phone,
        double latitude,
        double longitude,
        Double distanceKm,
        String websiteHint,
        String navigationUrl
) {
}
