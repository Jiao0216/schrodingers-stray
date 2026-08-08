package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.ShelterInfoResponse;
import com.catrescue.api.tracking.persistence.ShelterInfoEntity;
import com.catrescue.api.tracking.repository.ShelterInfoJpaRepository;
import com.catrescue.api.util.GeoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ShelterInfoService {

    private final ShelterInfoJpaRepository shelterInfoJpaRepository;

    public ShelterInfoService(ShelterInfoJpaRepository shelterInfoJpaRepository) {
        this.shelterInfoJpaRepository = shelterInfoJpaRepository;
    }

    public List<ShelterInfoResponse> nearest(double lat, double lng, int limit, String serviceFilter) {
        String filter = normalizeServiceFilter(serviceFilter);
        return shelterInfoJpaRepository.findAll().stream()
                .filter(row -> matchesService(row, filter))
                .map(row -> {
                    double km = GeoUtils.haversineKm(lat, lng, row.getLatitude(), row.getLongitude());
                    return new Scored(row, km);
                })
                .sorted(Comparator.comparingDouble(Scored::distanceKm))
                .limit(Math.max(1, limit))
                .map(s -> toDto(s.row(), s.distanceKm() * 1000.0))
                .toList();
    }

    public ShelterInfoResponse getById(long id, double lat, double lng) {
        ShelterInfoEntity row = shelterInfoJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shelter not found"));
        double km = GeoUtils.haversineKm(lat, lng, row.getLatitude(), row.getLongitude());
        return toDto(row, km * 1000.0);
    }

    private static boolean matchesService(ShelterInfoEntity row, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String services = row.getServices() == null ? "" : row.getServices().toLowerCase(Locale.ROOT);
        return services.contains(filter.toLowerCase(Locale.ROOT));
    }

    private static String normalizeServiceFilter(String serviceFilter) {
        if (serviceFilter == null || serviceFilter.isBlank()) {
            return null;
        }
        return serviceFilter.trim();
    }

    private static ShelterInfoResponse toDto(ShelterInfoEntity row, double distanceMeters) {
        List<String> tags = Arrays.stream((row.getServices() == null ? "" : row.getServices()).split("[,，/|]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new ShelterInfoResponse(
                row.getId(),
                row.getName(),
                row.getAddress(),
                row.getLatitude(),
                row.getLongitude(),
                row.getPhone(),
                tags,
                row.getNotes(),
                Math.round(distanceMeters * 10.0) / 10.0
        );
    }

    private record Scored(ShelterInfoEntity row, double distanceKm) {
    }
}
