package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.domain.DedupStatus;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.catrescue.api.tracking.dto.DedupDecision;
import com.catrescue.api.tracking.dto.NewSightingCommand;
import com.catrescue.api.tracking.dto.SightingReviewItemResponse;
import com.catrescue.api.tracking.event.TrackingSightingRecordedEvent;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.repository.UserJpaRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class SightingDeduplicationService {

    public static final double DUP_RADIUS_METERS = 500.0;
    public static final Duration DUP_WINDOW = Duration.ofHours(48);
    public static final double DUP_THRESHOLD = 0.72;
    // Keep image_url short for legacy DB schemas (often VARCHAR(255/512)).
    // Real image bytes are persisted separately in imageBytes.
    public static final int MAX_STORED_IMAGE_URL_CHARS = 240;

    private final SightingJpaRepository sightingJpaRepository;
    private final CatJpaRepository catJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CatFeatureExtractionService featureExtractionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SightingDeduplicationService(
            SightingJpaRepository sightingJpaRepository,
            CatJpaRepository catJpaRepository,
            UserJpaRepository userJpaRepository,
            CatFeatureExtractionService featureExtractionService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.sightingJpaRepository = sightingJpaRepository;
        this.catJpaRepository = catJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.featureExtractionService = featureExtractionService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public SightingEntity reportSighting(NewSightingCommand cmd) {
        if (cmd.reporterUserId() == null || cmd.reporterUserId() <= 0) {
            throw new IllegalArgumentException("reporterUserId must be a positive number");
        }
        ensureReporterUserExists(cmd.reporterUserId());
        Instant occurredAt = cmd.occurredAt() != null ? cmd.occurredAt() : Instant.now();
        CatFeatureVector incoming = featureExtractionService.extractFromImage(cmd.imageUrl());
        DedupDecision decision = detectDuplicate(cmd.latitude(), cmd.longitude(), occurredAt, incoming);

        SightingEntity entity = new SightingEntity();
        entity.setReporterUserId(cmd.reporterUserId());
        entity.setImageUrl(safeImageReferenceForStorage(cmd.imageUrl()));
        ParsedDataImage parsedDataImage = parseDataImage(cmd.imageUrl());
        if (parsedDataImage != null) {
            entity.setImageBytes(parsedDataImage.bytes());
            entity.setImageContentType(parsedDataImage.contentType());
        }
        entity.setAddressText(cmd.addressText());
        entity.setLatitude(cmd.latitude());
        entity.setLongitude(cmd.longitude());
        entity.setOccurredAt(occurredAt);
        entity.setCoatColor(normalizeCategory(incoming.coatColor()));
        entity.setPatternType(normalizeCategory(incoming.patternType()));
        entity.setBodySize(normalizeBodySize(incoming.bodySize()));
        entity.setSpecialFeatures(incoming.specialFeatures());
        entity.setEarTipped(incoming.earTipped());
        entity.setEarTippedConfidence(incoming.earTippedConfidence());
        entity.setFeatureExtractionConfidence(incoming.extractionConfidence());

        if (decision.duplicateLikely()) {
            entity.setDedupStatus(DedupStatus.PENDING_REVIEW);
            entity.setSuggestedCatId(decision.suggestedCatId());
            entity.setDuplicateOfSightingId(decision.matchedSightingId());
            entity.setSimilarityScore(decision.bestSimilarityScore());
            entity.setDedupReason(decision.reason());
        } else {
            CatEntity cat = createNewCat(cmd, incoming, occurredAt);
            entity.setCatId(cat.getId());
            entity.setDedupStatus(DedupStatus.NEW_CONFIRMED);
            entity.setDedupReason("No similar sighting within 500m/48h over threshold");
        }

        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        SightingEntity saved = sightingJpaRepository.save(entity);
        applicationEventPublisher.publishEvent(new TrackingSightingRecordedEvent(this, saved.getId()));
        return saved;
    }

    @Transactional
    public SightingEntity confirmDuplicate(Long sightingId, Long catIdToMergeInto, String reason) {
        SightingEntity sighting = sightingJpaRepository.findById(sightingId)
                .orElseThrow(() -> new IllegalArgumentException("Sighting not found: " + sightingId));
        CatEntity cat = catJpaRepository.findById(catIdToMergeInto)
                .orElseThrow(() -> new IllegalArgumentException("Cat not found: " + catIdToMergeInto));

        sighting.setCatId(cat.getId());
        sighting.setDedupStatus(DedupStatus.CONFIRMED_DUPLICATE);
        sighting.setDedupReason(reason != null ? reason : "Volunteer confirmed duplicate");
        sighting.setSuggestedCatId(null);
        sighting.setUpdatedAt(Instant.now());

        mergeSightingIntoCatProfile(cat, sighting);
        catJpaRepository.save(cat);
        return sightingJpaRepository.save(sighting);
    }

    @Transactional
    public SightingEntity rejectDuplicate(Long sightingId, String reason) {
        SightingEntity sighting = sightingJpaRepository.findById(sightingId)
                .orElseThrow(() -> new IllegalArgumentException("Sighting not found: " + sightingId));
        if (sighting.getDedupStatus() != DedupStatus.PENDING_REVIEW) {
            return sighting;
        }

        CatFeatureVector v = new CatFeatureVector(
                sighting.getCoatColor(),
                sighting.getPatternType(),
                sighting.getBodySize(),
                Optional.ofNullable(sighting.getSpecialFeatures()).orElse(List.of()),
                sighting.isEarTipped(),
                sighting.getEarTippedConfidence(),
                sighting.getFeatureExtractionConfidence(),
                ""
        );
        CatEntity cat = createNewCat(
                new NewSightingCommand(
                        sighting.getReporterUserId(),
                        sighting.getImageUrl(),
                        sighting.getAddressText(),
                        sighting.getLatitude(),
                        sighting.getLongitude(),
                        sighting.getOccurredAt()
                ),
                v,
                sighting.getOccurredAt()
        );

        sighting.setCatId(cat.getId());
        sighting.setDedupStatus(DedupStatus.REJECTED_DUPLICATE);
        sighting.setSuggestedCatId(null);
        sighting.setDuplicateOfSightingId(null);
        sighting.setDedupReason(reason != null ? reason : "Volunteer rejected duplicate suggestion");
        sighting.setUpdatedAt(Instant.now());
        return sightingJpaRepository.save(sighting);
    }

    public DedupDecision detectDuplicate(double lat, double lng, Instant occurredAt, CatFeatureVector incoming) {
        Instant since = occurredAt.minus(DUP_WINDOW);
        List<SightingEntity> candidates = sightingJpaRepository.findCandidatesWithinRadiusAndTime(
                lat, lng, since, DUP_RADIUS_METERS
        );
        if (candidates.isEmpty()) {
            return new DedupDecision(false, null, null, null, "No candidates in radius/time window");
        }

        double best = -1.0;
        SightingEntity bestCandidate = null;
        for (SightingEntity c : candidates) {
            double score = similarityVectorToSighting(incoming, c);
            if (score > best) {
                best = score;
                bestCandidate = c;
            }
        }
        if (bestCandidate == null || best < DUP_THRESHOLD) {
            return new DedupDecision(false, best, null, null, "Best similarity below threshold");
        }
        return new DedupDecision(
                true,
                best,
                bestCandidate.getId(),
                bestCandidate.getCatId(),
                "Candidate exceeds threshold with score=" + String.format(Locale.ROOT, "%.3f", best)
        );
    }

    public Page<SightingReviewItemResponse> listReviewSightings(boolean pendingOnly, int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<SightingEntity> entities = pendingOnly
                ? sightingJpaRepository.findByDedupStatusOrderByOccurredAtDesc(DedupStatus.PENDING_REVIEW, pageable)
                : sightingJpaRepository.findAllByOrderByOccurredAtDesc(pageable);
        return entities.map(SightingDeduplicationService::toReviewItem);
    }

    /**
     * Same weighted blend as duplicate detection: compares extracted upload features to a stored sighting row.
     */
    public static double similarityVectorToSighting(CatFeatureVector incoming, SightingEntity candidate) {
        double coat = equalityScore(incoming.coatColor(), candidate.getCoatColor());
        double pattern = equalityScore(incoming.patternType(), candidate.getPatternType());
        double size = equalityScore(incoming.bodySize(), candidate.getBodySize());
        double ear = booleanScore(incoming.earTipped(), candidate.isEarTipped());
        double features = overlapScore(incoming.specialFeatures(), candidate.getSpecialFeatures());

        // Weighted blend for business requirements.
        return 0.25 * coat + 0.20 * pattern + 0.15 * size + 0.30 * features + 0.10 * ear;
    }

    private CatEntity createNewCat(NewSightingCommand cmd, CatFeatureVector featureVector, Instant seenAt) {
        CatEntity cat = new CatEntity();
        cat.setDisplayName("Cat-" + seenAt.getEpochSecond());
        cat.setCoatColor(normalizeCategory(featureVector.coatColor()));
        cat.setPatternType(normalizeCategory(featureVector.patternType()));
        cat.setBodySize(normalizeBodySize(featureVector.bodySize()));
        cat.setSpecialFeatures(featureVector.specialFeatures());
        cat.setEarTipped(featureVector.earTipped());
        cat.setSterilizationConfidence(featureVector.earTippedConfidence());
        cat.setSterilizationStatus(featureVector.earTipped()
                ? SterilizationStatus.LIKELY_STERILIZED
                : SterilizationStatus.UNKNOWN);
        cat.setFirstSeenAt(seenAt);
        cat.setLastSeenAt(seenAt);
        cat.setLastSeenLat(cmd.latitude());
        cat.setLastSeenLng(cmd.longitude());
        cat.setCreatedByUserId(cmd.reporterUserId());
        cat.setCreatedAt(Instant.now());
        cat.setUpdatedAt(Instant.now());
        return catJpaRepository.save(cat);
    }

    private static void mergeSightingIntoCatProfile(CatEntity cat, SightingEntity sighting) {
        cat.setLastSeenAt(maxInstant(cat.getLastSeenAt(), sighting.getOccurredAt()));
        cat.setLastSeenLat(sighting.getLatitude());
        cat.setLastSeenLng(sighting.getLongitude());

        if (isUnknown(cat.getCoatColor())) {
            cat.setCoatColor(normalizeCategory(sighting.getCoatColor()));
        }
        if (isUnknown(cat.getPatternType())) {
            cat.setPatternType(normalizeCategory(sighting.getPatternType()));
        }
        if (isUnknown(cat.getBodySize())) {
            cat.setBodySize(normalizeBodySize(sighting.getBodySize()));
        }

        cat.setSpecialFeatures(unionFeatures(cat.getSpecialFeatures(), sighting.getSpecialFeatures()));

        // New activity cancels any pending "no re-sight" reminder state for this profile.
        cat.setAbsenceReminderSentAt(null);

        if (sighting.isEarTipped() && sighting.getEarTippedConfidence() >= 0.60) {
            cat.setEarTipped(true);
            cat.setSterilizationStatus(SterilizationStatus.LIKELY_STERILIZED);
            cat.setSterilizationConfidence(Math.max(
                    optionalDouble(cat.getSterilizationConfidence()),
                    sighting.getEarTippedConfidence()
            ));
        }
        cat.setUpdatedAt(Instant.now());
    }

    private static List<String> unionFeatures(List<String> oldFeatures, List<String> newFeatures) {
        Set<String> merged = new HashSet<>(normalizeSet(oldFeatures));
        merged.addAll(normalizeSet(newFeatures));
        return merged.stream().sorted().toList();
    }

    private static boolean isUnknown(String value) {
        String n = normalize(value);
        return n.isBlank() || "unknown".equals(n);
    }

    private static double optionalDouble(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double equalityScore(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        return normalize(a).equals(normalize(b)) ? 1.0 : 0.0;
    }

    private static double booleanScore(boolean a, boolean b) {
        return a == b ? 1.0 : 0.0;
    }

    private static double overlapScore(List<String> a, List<String> b) {
        Set<String> left = normalizeSet(a);
        Set<String> right = normalizeSet(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String x : left) {
            if (right.contains(x)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static Set<String> normalizeSet(List<String> in) {
        if (in == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String x : in) {
            String n = normalize(x);
            if (!n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }

    private static String normalize(String x) {
        return x == null ? "" : x.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCategory(String x) {
        String s = normalize(x);
        if (s.isBlank() || s.contains("无法判断") || s.contains("unknown")) {
            return "unknown";
        }
        s = s.replaceAll("[^a-z0-9 _-]", "").replaceAll("\\s+", " ").trim();
        return s.isBlank() ? "unknown" : s;
    }

    private static String normalizeBodySize(String x) {
        String s = normalizeCategory(x);
        return switch (s) {
            case "small", "medium", "large" -> s;
            default -> "unknown";
        };
    }

    private static String safeImageReferenceForStorage(String imageUrl) {
        String value = imageUrl == null ? "" : imageUrl;
        if (value.startsWith("data:")) {
            return "data-url:sha256=" + sha256Hex(value) + ":chars=" + value.length();
        }
        if (value.length() <= MAX_STORED_IMAGE_URL_CHARS) {
            return value;
        }
        // Keep DB write safe for very large uploads; retain a stable identifier for auditing.
        return "data-url-too-large:sha256=" + sha256Hex(value) + ":chars=" + value.length();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private static ParsedDataImage parseDataImage(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("data:")) {
            return null;
        }
        int comma = value.indexOf(',');
        if (comma <= 5) {
            return null;
        }
        String meta = value.substring(5, comma);
        String payload = value.substring(comma + 1);
        String contentType = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        boolean base64 = meta.toLowerCase(Locale.ROOT).contains(";base64");
        try {
            byte[] bytes = base64 ? Base64.getDecoder().decode(payload) : payload.getBytes(StandardCharsets.UTF_8);
            return new ParsedDataImage(bytes, contentType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record ParsedDataImage(byte[] bytes, String contentType) {
    }

    private void ensureReporterUserExists(Long reporterUserId) {
        String email = "tracking-user-" + reporterUserId + "@local.invalid";
        Optional<UserEntity> byId = userJpaRepository.findById(reporterUserId);
        if (byId.isPresent()) {
            UserEntity existing = byId.get();
            boolean dirty = false;
            if (existing.getEmail() == null || existing.getEmail().isBlank()) {
                existing.setEmail(email);
                dirty = true;
            }
            if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                existing.setDisplayName("Tracking User " + reporterUserId);
                dirty = true;
            }
            if (existing.getRole() == null) {
                existing.setRole(UserRole.VOLUNTEER);
                dirty = true;
            }
            if (dirty) {
                userJpaRepository.save(existing);
            }
            return;
        }

        Optional<UserEntity> byEmail = userJpaRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            UserEntity existing = byEmail.get();
            boolean dirty = false;
            if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                existing.setDisplayName("Tracking User " + reporterUserId);
                dirty = true;
            }
            if (existing.getRole() == null) {
                existing.setRole(UserRole.VOLUNTEER);
                dirty = true;
            }
            if (dirty) {
                userJpaRepository.save(existing);
            }
            return;
        }

        UserEntity u = new UserEntity();
        // Accept explicit caller-provided id so foreign key reporter_user_id can be used immediately.
        u.setId(reporterUserId);
        u.setEmail(email);
        u.setDisplayName("Tracking User " + reporterUserId);
        u.setRole(UserRole.VOLUNTEER);
        u.setCreatedAt(Instant.now());
        try {
            userJpaRepository.save(u);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert or legacy row with same key: treat as existing user (idempotent upsert).
            if (userJpaRepository.findById(reporterUserId).isEmpty()
                    && userJpaRepository.findByEmail(email).isEmpty()) {
                throw ex;
            }
        }
    }

    private static Instant maxInstant(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static SightingReviewItemResponse toReviewItem(SightingEntity s) {
        return new SightingReviewItemResponse(
                s.getId(),
                s.getCatId(),
                s.getReporterUserId(),
                s.getImageUrl(),
                s.getAddressText(),
                s.getLatitude(),
                s.getLongitude(),
                s.getOccurredAt(),
                s.getCoatColor(),
                s.getPatternType(),
                s.getBodySize(),
                s.getSpecialFeatures(),
                s.isEarTipped(),
                s.getEarTippedConfidence(),
                s.getDedupStatus(),
                s.getSuggestedCatId(),
                s.getDuplicateOfSightingId(),
                s.getSimilarityScore(),
                s.getDedupReason(),
                s.getCreatedAt()
        );
    }
}
