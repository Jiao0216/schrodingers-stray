package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.catrescue.api.tracking.dto.CatPhotoMatchItemResponse;
import com.catrescue.api.tracking.dto.CatPhotoMatchResponse;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Extracts features from an uploaded photo and ranks archive cats by the same similarity metric as sighting dedup.
 */
@Service
public class CatPhotoMatchService {

    public static final int TOP_K = 3;

    private final CatFeatureExtractionService featureExtractionService;
    private final SightingJpaRepository sightingJpaRepository;
    private final CatJpaRepository catJpaRepository;

    public CatPhotoMatchService(
            CatFeatureExtractionService featureExtractionService,
            SightingJpaRepository sightingJpaRepository,
            CatJpaRepository catJpaRepository
    ) {
        this.featureExtractionService = featureExtractionService;
        this.sightingJpaRepository = sightingJpaRepository;
        this.catJpaRepository = catJpaRepository;
    }

    public CatPhotoMatchResponse matchByPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        String dataUrl = toDataUrl(file);
        CatFeatureVector incoming = featureExtractionService.extractFromImage(dataUrl);

        List<Long> catIds = sightingJpaRepository.findDistinctCatIds();
        record Scored(long catId, double score, SightingEntity sighting) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Long catId : catIds) {
            Optional<SightingEntity> latest = sightingJpaRepository.findFirstByCatIdOrderByOccurredAtDesc(catId);
            if (latest.isEmpty()) {
                continue;
            }
            SightingEntity s = latest.get();
            double score = SightingDeduplicationService.similarityVectorToSighting(incoming, s);
            scored.add(new Scored(catId, score, s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<CatPhotoMatchItemResponse> top = new ArrayList<>();
        int limit = Math.min(TOP_K, scored.size());
        for (int i = 0; i < limit; i++) {
            Scored row = scored.get(i);
            CatEntity cat = catJpaRepository.findById(row.catId()).orElse(null);
            Instant lastSeen = cat != null ? cat.getLastSeenAt() : row.sighting().getOccurredAt();
            double lat = cat != null ? cat.getLastSeenLat() : row.sighting().getLatitude();
            double lng = cat != null ? cat.getLastSeenLng() : row.sighting().getLongitude();
            String loc = buildLocationSummary(row.sighting(), lat, lng);
            String imageUrl = row.sighting().getImageUrl() != null ? row.sighting().getImageUrl() : "";
            top.add(new CatPhotoMatchItemResponse(
                    row.catId(),
                    roundScore(row.score()),
                    imageUrl,
                    loc,
                    lat,
                    lng,
                    lastSeen
            ));
        }
        return new CatPhotoMatchResponse(top);
    }

    private static double roundScore(double s) {
        return Math.round(s * 1000.0) / 1000.0;
    }

    private static String buildLocationSummary(SightingEntity sighting, double lat, double lng) {
        String addr = sighting.getAddressText();
        if (addr != null && !addr.isBlank()) {
            return addr.trim();
        }
        return String.format(Locale.ROOT, "%.4f, %.4f", lat, lng);
    }

    private static String toDataUrl(MultipartFile file) {
        try {
            byte[] raw = file.getBytes();
            String mime = file.getContentType();
            if (mime == null || mime.isBlank() || !mime.startsWith("image/")) {
                mime = guessMimeFromName(file.getOriginalFilename());
            }
            String b64 = Base64.getEncoder().encodeToString(raw);
            return "data:" + mime + ";base64," + b64;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read image: " + e.getMessage(), e);
        }
    }

    private static String guessMimeFromName(String name) {
        if (name == null) {
            return "image/jpeg";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
