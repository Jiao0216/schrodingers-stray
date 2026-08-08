package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.FeedingSpotCheckInResponse;
import com.catrescue.api.tracking.dto.FeedingSpotDetailResponse;
import com.catrescue.api.tracking.dto.FeedingSpotRecordResponse;
import com.catrescue.api.tracking.dto.FeedingSpotResponse;
import com.catrescue.api.tracking.persistence.FeedingSpotEntity;
import com.catrescue.api.tracking.persistence.FeedingSpotRecordEntity;
import com.catrescue.api.tracking.repository.FeedingSpotJpaRepository;
import com.catrescue.api.tracking.repository.FeedingSpotRecordJpaRepository;
import com.catrescue.api.util.GeoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class FeedingSpotService {

    public static final double DEFAULT_NEARBY_RADIUS_M = 800.0;

    private final FeedingSpotJpaRepository feedingSpotJpaRepository;
    private final FeedingSpotRecordJpaRepository feedingSpotRecordJpaRepository;

    public FeedingSpotService(
            FeedingSpotJpaRepository feedingSpotJpaRepository,
            FeedingSpotRecordJpaRepository feedingSpotRecordJpaRepository
    ) {
        this.feedingSpotJpaRepository = feedingSpotJpaRepository;
        this.feedingSpotRecordJpaRepository = feedingSpotRecordJpaRepository;
    }

    public List<FeedingSpotResponse> listActive() {
        return feedingSpotJpaRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(spot -> toSummary(spot, null))
                .toList();
    }

    public List<FeedingSpotResponse> nearestActive(double lat, double lng, double radiusM, int limit) {
        double radiusKm = radiusM / 1000.0;
        return feedingSpotJpaRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(spot -> {
                    double km = GeoUtils.haversineKm(lat, lng, spot.getLatitude(), spot.getLongitude());
                    return new SpotScored(spot, km);
                })
                .filter(s -> s.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(SpotScored::distanceKm))
                .limit(Math.max(1, limit))
                .map(s -> toSummary(s.spot(), s.distanceKm() * 1000.0))
                .toList();
    }

    public FeedingSpotDetailResponse getDetail(long id, int recordLimit) {
        FeedingSpotEntity spot = feedingSpotJpaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feeding spot not found"));
        List<FeedingSpotRecordResponse> records = feedingSpotRecordJpaRepository
                .findByFeedingSpotIdOrderByFedAtDesc(id).stream()
                .limit(Math.max(1, recordLimit))
                .map(FeedingSpotService::toRecordDto)
                .toList();
        return new FeedingSpotDetailResponse(toSummary(spot, null), records);
    }

    public Optional<byte[]> getPhotoBytes(long id) {
        return feedingSpotJpaRepository.findById(id).map(FeedingSpotEntity::getPhotoBytes);
    }

    public Optional<String> getPhotoContentType(long id) {
        return feedingSpotJpaRepository.findById(id).map(FeedingSpotEntity::getPhotoContentType);
    }

    @Transactional
    public FeedingSpotResponse create(
            MultipartFile photo,
            double latitude,
            double longitude,
            String description,
            Long createdByUserId
    ) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Bowl photo is required to create a feeding spot");
        }
        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read bowl photo", e);
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Bowl photo is required to create a feeding spot");
        }
        FeedingSpotEntity row = new FeedingSpotEntity();
        row.setLatitude(latitude);
        row.setLongitude(longitude);
        row.setDescription(trim(description));
        row.setCreatedByUserId(createdByUserId);
        row.setPhotoBytes(bytes);
        row.setPhotoContentType(photo.getContentType());
        row.setCreatedAt(Instant.now());
        row.setActive(true);
        FeedingSpotEntity saved = feedingSpotJpaRepository.save(row);
        return toSummary(saved, null);
    }

    @Transactional
    public FeedingSpotCheckInResponse checkIn(long spotId, Long fedByUserId, String note) {
        FeedingSpotEntity spot = feedingSpotJpaRepository.findById(spotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feeding spot not found"));
        if (!spot.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feeding spot is inactive");
        }
        Instant now = Instant.now();
        FeedingSpotRecordEntity record = new FeedingSpotRecordEntity();
        record.setFeedingSpotId(spotId);
        record.setFedByUserId(fedByUserId);
        record.setFedAt(now);
        record.setNote(trim(note));
        record.setCreatedAt(now);
        FeedingSpotRecordEntity saved = feedingSpotRecordJpaRepository.save(record);
        return new FeedingSpotCheckInResponse(true, now, saved.getId());
    }

    private FeedingSpotResponse toSummary(FeedingSpotEntity spot, Double distanceMeters) {
        List<FeedingSpotRecordEntity> recent = feedingSpotRecordJpaRepository
                .findByFeedingSpotIdOrderByFedAtDesc(spot.getId());
        Instant lastFed = recent.isEmpty() ? null : recent.get(0).getFedAt();
        return new FeedingSpotResponse(
                spot.getId(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getDescription(),
                spot.getCreatedByUserId(),
                spot.getCreatedAt(),
                spot.isActive(),
                "/api/v1/feeding-spots/" + spot.getId() + "/photo",
                distanceMeters,
                lastFed,
                recent.size()
        );
    }

    private static FeedingSpotRecordResponse toRecordDto(FeedingSpotRecordEntity r) {
        return new FeedingSpotRecordResponse(
                r.getId(),
                r.getFeedingSpotId(),
                r.getFedByUserId(),
                r.getFedAt(),
                r.getNote()
        );
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private record SpotScored(FeedingSpotEntity spot, double distanceKm) {
    }
}
