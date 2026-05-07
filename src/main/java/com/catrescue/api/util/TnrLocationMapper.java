package com.catrescue.api.util;

import com.catrescue.api.domain.TnrLocation;
import com.catrescue.api.dto.TnrLocationDto;

public final class TnrLocationMapper {

    private TnrLocationMapper() {
    }

    public static TnrLocationDto toDto(TnrLocation e, double fromLat, double fromLng) {
        double km = GeoUtils.haversineKm(fromLat, fromLng, e.latitude(), e.longitude());
        String navigationUrl = "https://www.google.com/maps/dir/?api=1&destination="
                + e.latitude() + "," + e.longitude();
        return new TnrLocationDto(
                e.id(),
                e.name(),
                e.shortDescription(),
                e.city(),
                e.streetAddress(),
                e.phone(),
                e.latitude(),
                e.longitude(),
                Math.round(km * 100.0) / 100.0,
                e.websiteHint(),
                navigationUrl
        );
    }
}
