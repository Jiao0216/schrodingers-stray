package com.catrescue.api.domain;

/**
 * Partner or public program near San Jose — replace with DB + verified listings for production.
 */
public record TnrLocation(
        String id,
        String name,
        String shortDescription,
        String city,
        String streetAddress,
        String phone,
        double latitude,
        double longitude,
        String websiteHint
) {
}
