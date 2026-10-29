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
        String navigationUrl,
        /* Chinese copy (null = use the default field as-is, e.g. a real organization name). */
        String nameZh,
        String shortDescriptionZh,
        String streetAddressZh
) {
}
