package com.catrescue.api.tracking.controller;

import com.catrescue.api.config.SiteProperties;
import com.catrescue.api.persistence.AssessmentEntity;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import com.catrescue.api.tracking.dto.NewSightingCommand;
import com.catrescue.api.tracking.dto.ReportSightingRequest;
import com.catrescue.api.tracking.dto.SightingReviewItemResponse;
import com.catrescue.api.tracking.dto.SightingDecisionResponse;
import com.catrescue.api.tracking.dto.SightingListItemResponse;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.service.SightingDeduplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping({"/api/v1/tracking/sightings", "/api/v1/sightings"})
public class SightingController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SightingDeduplicationService deduplicationService;
    private final SightingJpaRepository sightingJpaRepository;
    private final AssessmentJpaRepository assessmentJpaRepository;
    private final SiteProperties siteProperties;

    public SightingController(
            SightingDeduplicationService deduplicationService,
            SightingJpaRepository sightingJpaRepository,
            AssessmentJpaRepository assessmentJpaRepository,
            SiteProperties siteProperties
    ) {
        this.deduplicationService = deduplicationService;
        this.sightingJpaRepository = sightingJpaRepository;
        this.assessmentJpaRepository = assessmentJpaRepository;
        this.siteProperties = siteProperties;
    }

    @PostMapping
    public SightingDecisionResponse report(@Valid @RequestBody ReportSightingRequest request) {
        SightingEntity sighting = deduplicationService.reportSighting(new NewSightingCommand(
                request.reporterUserId(),
                request.imageUrl(),
                request.addressText(),
                request.latitude(),
                request.longitude(),
                request.occurredAt()
        ));
        return toResponse(sighting);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SightingDecisionResponse reportWithImageUpload(
            @RequestParam @NotNull Long reporterUserId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String addressText,
            @RequestParam @NotNull Double latitude,
            @RequestParam @NotNull Double longitude,
            @RequestParam(required = false) String occurredAt
    ) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image file is required");
        }
        String mime = image.getContentType() != null ? image.getContentType() : "image/jpeg";
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image.getBytes());
        Instant occurred = (occurredAt == null || occurredAt.isBlank()) ? null : Instant.parse(occurredAt);

        SightingEntity sighting = deduplicationService.reportSighting(new NewSightingCommand(
                reporterUserId,
                dataUrl,
                addressText,
                latitude,
                longitude,
                occurred
        ));
        return toResponse(sighting);
    }

    @PostMapping("/{sightingId}/confirm-duplicate")
    public SightingDecisionResponse confirmDuplicate(
            @PathVariable Long sightingId,
            @RequestParam Long catId,
            @RequestParam(required = false) String reason
    ) {
        return toResponse(deduplicationService.confirmDuplicate(sightingId, catId, reason));
    }

    @PostMapping("/{sightingId}/reject-duplicate")
    public SightingDecisionResponse rejectDuplicate(
            @PathVariable Long sightingId,
            @RequestParam(required = false) String reason
    ) {
        return toResponse(deduplicationService.rejectDuplicate(sightingId, reason));
    }

    @GetMapping("/review")
    public Page<SightingReviewItemResponse> reviewList(
            @RequestParam(defaultValue = "true") boolean pendingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return deduplicationService.listReviewSightings(pendingOnly, page, size);
    }

    @GetMapping
    public List<SightingListItemResponse> mySightings(
            @RequestParam @NotNull Long userId,
            HttpServletRequest request
    ) {
        return sightingJpaRepository.findByReporterUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, 50))
                .stream()
                .map(s -> toListItem(s, request))
                .toList();
    }

    @GetMapping({"/my", "/my/"})
    public List<SightingListItemResponse> mySightingsByToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        long userId = parseUserIdFromToken(authorization);
        return sightingJpaRepository.findByReporterUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, 3))
                .stream()
                .map(s -> toListItem(s, request))
                .toList();
    }

    @GetMapping("/{sightingId}/image")
    @SuppressWarnings("null")
    public ResponseEntity<byte[]> sightingImage(@PathVariable Long sightingId) {
        SightingEntity s = sightingJpaRepository.findById(sightingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sighting not found"));
        if (s.getImageBytes() != null && s.getImageBytes().length > 0) {
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (s.getImageContentType() != null && !s.getImageContentType().isBlank()) {
                    mt = MediaType.parseMediaType(s.getImageContentType());
                }
            } catch (Exception _ignore) {
                mt = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mt)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .body(s.getImageBytes());
        }
        String raw = s.getImageUrl();
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "image not found");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("data:")) {
            int comma = trimmed.indexOf(',');
            if (comma <= 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid data url");
            }
            String meta = trimmed.substring(5, comma);
            String payload = trimmed.substring(comma + 1);
            String contentType = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            boolean base64 = meta.toLowerCase(Locale.ROOT).contains(";base64");
            byte[] bytes;
            try {
                bytes = base64 ? Base64.getDecoder().decode(payload) : payload.getBytes();
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image payload");
            }
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (contentType != null && !contentType.isBlank()) {
                    mt = MediaType.parseMediaType(contentType);
                }
            } catch (Exception _ignore) {
                mt = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mt)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .body(bytes);
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, trimmed)
                    .build();
        }
        AssessmentEntity candidate = findClosestAssessmentWithImage(s);
        if (candidate != null && candidate.getImageBytes() != null && candidate.getImageBytes().length > 0) {
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            try {
                if (candidate.getContentType() != null && !candidate.getContentType().isBlank()) {
                    mt = MediaType.parseMediaType(candidate.getContentType());
                }
            } catch (Exception _ignore) {
                mt = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mt)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .body(candidate.getImageBytes());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "image unavailable");
    }

    private AssessmentEntity findClosestAssessmentWithImage(SightingEntity s) {
        List<AssessmentEntity> rows = assessmentJpaRepository.findTop500ByOrderByCreatedAtDesc();
        if (rows.isEmpty()) {
            return null;
        }
        AssessmentEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Instant occurredAt = s.getOccurredAt() != null ? s.getOccurredAt() : Instant.now();
        for (AssessmentEntity a : rows) {
            if (!a.isImageStored() || a.getImageBytes() == null || a.getImageBytes().length == 0) {
                continue;
            }
            if (a.getLatitude() == null || a.getLongitude() == null || s.getLatitude() == 0.0 && s.getLongitude() == 0.0) {
                continue;
            }
            double meter = distanceMeters(s.getLatitude(), s.getLongitude(), a.getLatitude(), a.getLongitude());
            long dt = Math.abs(Duration.between(occurredAt, a.getCreatedAt()).getSeconds());
            if (meter > 120.0 || dt > 900) {
                continue;
            }
            double score = meter + (dt / 3.0);
            if (score < bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double rEarth = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1 - a)));
        return rEarth * c;
    }

    private SightingListItemResponse toListItem(SightingEntity s, HttpServletRequest request) {
        return new SightingListItemResponse(
                s.getId(),
                s.getCatId(),
                toAbsoluteImageUrl(s.getImageUrl(), request),
                s.getOccurredAt(),
                s.getAddressText(),
                s.getDedupStatus() != null ? s.getDedupStatus().name() : "",
                s.getDedupReason(),
                s.getSimilarityScore(),
                summarizeAiHealth(s)
        );
    }

    private String summarizeAiHealth(SightingEntity s) {
        if (s.getDedupStatus() == null) {
            return "待分析";
        }
        return switch (s.getDedupStatus()) {
            case NEW_CONFIRMED -> "状态稳定，建议持续观察";
            case PENDING_REVIEW -> "模型建议复核，可能需要进一步关注";
            case CONFIRMED_DUPLICATE -> "已确认为重复记录";
            case REJECTED_DUPLICATE -> "已确认是新线索，建议继续跟进";
            case DUPLICATE_SUSPECTED -> "疑似重复记录，建议人工复核";
            case NEW_CAT -> "疑似新猫线索，建议持续跟进";
        };
    }

    private long parseUserIdFromToken(String authorization) {
        String raw = authorization == null ? "" : authorization.trim();
        if (raw.isBlank() || !raw.toLowerCase().startsWith("bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = raw.substring(7).trim();
        if (token.startsWith("cg_")) {
            String[] parts = token.split("_");
            if (parts.length < 3) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token format");
            }
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token user id");
            }
        }
        if (token.matches("^[1-9]\\d*$")) {
            return Long.parseLong(token);
        }
        try {
            String[] jwtParts = token.split("\\.");
            if (jwtParts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(jwtParts[1]);
                JsonNode payload = OBJECT_MAPPER.readTree(decoded);
                if (payload.hasNonNull("userId")) {
                    return payload.get("userId").asLong();
                }
                if (payload.hasNonNull("uid")) {
                    return payload.get("uid").asLong();
                }
                if (payload.hasNonNull("sub")) {
                    String sub = payload.get("sub").asText();
                    if (sub != null && sub.matches("^[1-9]\\d*$")) {
                        return Long.parseLong(sub);
                    }
                }
            }
        } catch (Exception _e) {
            // fall through
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
    }

    private String toAbsoluteImageUrl(String raw, HttpServletRequest request) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return trimmed;
        }
        String path = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        String configuredBase = siteProperties.getPublicBaseUrl() == null ? "" : siteProperties.getPublicBaseUrl().trim();
        if (!configuredBase.isBlank()) {
            String base = configuredBase.endsWith("/") ? configuredBase.substring(0, configuredBase.length() - 1) : configuredBase;
            return base + path;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        String origin = defaultPort ? (scheme + "://" + host) : (scheme + "://" + host + ":" + port);
        return origin + path;
    }

    private static SightingDecisionResponse toResponse(SightingEntity sighting) {
        return new SightingDecisionResponse(
                sighting.getId(),
                sighting.getCatId(),
                sighting.getDedupStatus(),
                sighting.getSuggestedCatId(),
                sighting.getDuplicateOfSightingId(),
                sighting.getSimilarityScore(),
                sighting.getDedupReason()
        );
    }
}
