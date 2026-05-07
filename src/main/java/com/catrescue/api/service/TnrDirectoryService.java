package com.catrescue.api.service;

import com.catrescue.api.domain.TnrLocation;
import com.catrescue.api.dto.TnrLocationDto;
import com.catrescue.api.util.GeoUtils;
import com.catrescue.api.util.TnrLocationMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Static South Bay placeholders. Curate names, eligibility, and hours before production.
 */
@Service
public class TnrDirectoryService {

    private static final List<TnrLocation> ENTRIES = List.of(
            new TnrLocation(
                    "sj-city",
                    "City of San Jose Animal Services",
                    "Municipal shelter — ask about community cats, TNR, and low-cost programs",
                    "San Jose",
                    "2750 Monterey Rd, San Jose, CA 95111",
                    "+1-408-794-7297",
                    37.3382,
                    -121.8863,
                    "https://www.sanjoseca.gov/your-government/departments/animal-care-services"
            ),
            new TnrLocation(
                    "hscc",
                    "Humane Society of Silicon Valley",
                    "TNR support and clinic services in the South Bay — confirm feral/community cat programs",
                    "Milpitas",
                    "901 Ames Ave, Milpitas, CA 95035",
                    "+1-408-262-2133",
                    37.4283,
                    -121.9067,
                    "https://www.hssv.org/"
            ),
            new TnrLocation(
                    "sbac",
                    "Santa Clara County area — check local shelters and rescue groups",
                    "County-wide — verify current TNR partnerships and voucher programs",
                    "Santa Clara",
                    "Service area varies by city and shelter partner",
                    "",
                    37.3541,
                    -121.9552,
                    null
            )
    );

    public List<TnrLocation> nearest(double lat, double lng, int limit) {
        return ENTRIES.stream()
                .sorted(Comparator.comparingDouble(e -> GeoUtils.haversineKm(lat, lng, e.latitude(), e.longitude())))
                .limit(limit)
                .toList();
    }

    public Optional<TnrLocation> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public List<TnrLocationDto> nearestDtos(double lat, double lng, int limit) {
        return nearest(lat, lng, limit).stream()
                .map(e -> TnrLocationMapper.toDto(e, lat, lng))
                .toList();
    }

    public Optional<TnrLocationDto> findDtoById(String id, double lat, double lng) {
        return findById(id).map(e -> TnrLocationMapper.toDto(e, lat, lng));
    }
}
