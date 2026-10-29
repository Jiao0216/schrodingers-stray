package com.catrescue.api.tracking.service;

import com.catrescue.api.service.client.ClipEmbeddingClient;
import com.catrescue.api.tracking.dto.ClipDedupDecision;
import com.catrescue.api.tracking.dto.DedupDecision;
import com.catrescue.api.tracking.dto.NewSightingCommand;
import com.catrescue.api.tracking.dto.SightingReviewItemResponse;
import com.catrescue.api.tracking.event.TrackingSightingRecordedEvent;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.CatMatchFeedbackEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.CatMatchFeedbackJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.repository.UserJpaRepository;
import com.catrescue.api.tracking.domain.DedupStatus;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.catrescue.api.tracking.vector.StrayCatVectorMetadata;
import com.catrescue.api.tracking.vector.VectorClient;
import com.catrescue.api.tracking.vector.VectorMatchResult;
import com.catrescue.api.util.CoordinatePrecision;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class SightingDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(SightingDeduplicationService.class);

    public static final double DUP_RADIUS_METERS = 500.0;
    public static final Duration DUP_WINDOW = Duration.ofHours(48);
    public static final double DUP_THRESHOLD = 0.72;
    public static final int CLIP_QUERY_TOP_K = 5;
    public static final int MAX_STORED_IMAGE_URL_CHARS = 240;

    private final SightingJpaRepository sightingJpaRepository;
    private final CatJpaRepository catJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CatMatchFeedbackJpaRepository catMatchFeedbackJpaRepository;
    private final CatFeatureExtractionService featureExtractionService;
    private final ClipEmbeddingClient clipEmbeddingClient;
    private final ClipThresholdCalibrationService clipThresholdCalibrationService;
    private final VectorClient vectorClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SightingDeduplicationService(
            SightingJpaRepository sightingJpaRepository,
            CatJpaRepository catJpaRepository,
            UserJpaRepository userJpaRepository,
            CatMatchFeedbackJpaRepository catMatchFeedbackJpaRepository,
            CatFeatureExtractionService featureExtractionService,
            ClipEmbeddingClient clipEmbeddingClient,
            ClipThresholdCalibrationService clipThresholdCalibrationService,
            VectorClient vectorClient,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.sightingJpaRepository = sightingJpaRepository;
        this.catJpaRepository = catJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.catMatchFeedbackJpaRepository = catMatchFeedbackJpaRepository;
        this.featureExtractionService = featureExtractionService;
        this.clipEmbeddingClient = clipEmbeddingClient;
        this.clipThresholdCalibrationService = clipThresholdCalibrationService;
        this.vectorClient = vectorClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public SightingEntity reportSighting(NewSightingCommand cmd) {
        if (cmd.reporterUserId() == null || cmd.reporterUserId() <= 0) {
            throw new IllegalArgumentException("reporterUserId must be a positive number");
        }
        ensureReporterUserExists(cmd.reporterUserId());
        Instant occurredAt = cmd.occurredAt() != null ? cmd.occurredAt() : Instant.now();

        ParsedDataImage parsedDataImage = parseDataImage(cmd.imageUrl());
        final byte[] imageBytes = parsedDataImage != null ? parsedDataImage.bytes() : null;
        final String imageContentType = parsedDataImage != null ? parsedDataImage.contentType() : "image/jpeg";

        CompletableFuture<CatFeatureVector> featureFuture = CompletableFuture.supplyAsync(() -> {
            byte[] headCrop = clipEmbeddingClient.cropHead(imageBytes, imageContentType).orElse(null);
            return featureExtractionService.extractFromImage(cmd.imageUrl(), headCrop);
        });
        CompletableFuture<ClipDedupOutcome> clipFuture = CompletableFuture.supplyAsync(
                () -> evaluateClipDedup(imageBytes, imageContentType)
        );

        CatFeatureVector incoming = featureFuture.join();
        ClipDedupOutcome clipOutcome = clipFuture.join();

        DedupDecision featureDecision = detectDuplicate(cmd.latitude(), cmd.longitude(), occurredAt, incoming);
        DedupSignalMerger.CombinedDedupDecision combined = DedupSignalMerger.merge(
                featureDecision,
                clipOutcome.decision()
        );

        SightingEntity entity = new SightingEntity();
        entity.setReporterUserId(cmd.reporterUserId());
        entity.setImageUrl(safeImageReferenceForStorage(cmd.imageUrl()));
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

        if (combined.pendingReview()) {
            entity.setDedupStatus(DedupStatus.PENDING_REVIEW);
            entity.setSuggestedCatId(combined.suggestedCatId());
            entity.setDuplicateOfSightingId(combined.duplicateOfSightingId());
            entity.setSimilarityScore(combined.featureSimilarityScore());
            entity.setDedupReason(combined.dedupReason());
        } else {
            CatEntity cat = createNewCat(cmd, incoming, occurredAt);
            entity.setCatId(cat.getId());
            entity.setDedupStatus(DedupStatus.NEW_CONFIRMED);
            entity.setSimilarityScore(combined.featureSimilarityScore());
            entity.setDedupReason(combined.dedupReason());
        }

        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        SightingEntity saved = sightingJpaRepository.save(entity);

        saveMatchFeedback(saved.getId(), featureDecision, clipOutcome.decision());
        indexSightingInChroma(saved, clipOutcome.embedding(), cmd.imageUrl());

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
        updateVolunteerFeedback(sightingId, true, null);
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
        updateVolunteerFeedback(sightingId, false, null);
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

    ClipDedupOutcome evaluateClipDedup(byte[] imageBytes, String contentType) {
        if (!clipEmbeddingClient.isEnabled()) {
            return new ClipDedupOutcome(ClipDedupDecision.unavailable("CLIP service disabled"), null);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return new ClipDedupOutcome(ClipDedupDecision.unavailable("No image bytes for CLIP"), null);
        }

        Optional<float[]> embedding = clipEmbeddingClient.embedBytes(imageBytes, "upload.jpg", contentType);
        if (embedding.isEmpty()) {
            return new ClipDedupOutcome(ClipDedupDecision.unavailable("CLIP embed unavailable"), null);
        }

        List<VectorMatchResult> matches = vectorClient.queryTopK(embedding.get(), CLIP_QUERY_TOP_K, null);
        if (matches.isEmpty()) {
            return new ClipDedupOutcome(
                    new ClipDedupDecision(false, null, null, null, "No CLIP vector candidates"),
                    embedding.get()
            );
        }

        VectorMatchResult best = matches.get(0);
        double threshold = clipThresholdCalibrationService.effectiveThreshold();
        boolean hit = best.distance() < threshold;
        String reason = hit
                ? "CLIP candidate distance=" + String.format(Locale.ROOT, "%.3f", best.distance())
                : "CLIP distance above threshold (" + String.format(Locale.ROOT, "%.3f", best.distance()) + ")";
        ClipDedupDecision decision = new ClipDedupDecision(
                hit,
                best.distance(),
                best.sightingId() > 0 ? best.sightingId() : null,
                best.catId(),
                reason
        );
        return new ClipDedupOutcome(decision, embedding.get());
    }

    private void saveMatchFeedback(Long sightingId, DedupDecision feature, ClipDedupDecision clip) {
        CatMatchFeedbackEntity row = new CatMatchFeedbackEntity();
        row.setSightingId(sightingId);
        row.setMatchedSightingId(firstNonNull(feature.matchedSightingId(), clip.matchedSightingId()));
        row.setMatchedCatId(firstNonNull(feature.suggestedCatId(), clip.matchedCatId()));
        row.setFeatureSimilarity(feature.bestSimilarityScore());
        row.setClipDistance(clip.bestDistance());
        row.setClipMatchedSightingId(clip.matchedSightingId());
        row.setEmbeddingVersion(clip.bestDistance() != null ? clipEmbeddingClient.embeddingVersion() : null);
        row.setVolunteerConfirmed(null);
        row.setCreatedAt(Instant.now());
        catMatchFeedbackJpaRepository.save(row);
    }

    private void updateVolunteerFeedback(Long sightingId, boolean confirmed, String confirmedBy) {
        catMatchFeedbackJpaRepository.findFirstBySightingIdOrderByIdDesc(sightingId).ifPresent(row -> {
            if (row.getVolunteerConfirmed() != null) {
                return;
            }
            row.setVolunteerConfirmed(confirmed);
            row.setConfirmedAt(Instant.now());
            row.setConfirmedBy(confirmedBy);
            catMatchFeedbackJpaRepository.save(row);
            clipThresholdCalibrationService.recalibrate();
        });
    }

    private void indexSightingInChroma(SightingEntity saved, float[] embedding, String rawImageUrl) {
        if (embedding == null || !vectorClient.isEnabled()) {
            return;
        }
        String chromaId = "sighting_" + saved.getId();
        double lat = CoordinatePrecision.roundDegrees(saved.getLatitude(), 2);
        double lng = CoordinatePrecision.roundDegrees(saved.getLongitude(), 2);
        StrayCatVectorMetadata metadata = new StrayCatVectorMetadata(
                saved.getId(),
                saved.getCatId(),
                saved.getOccurredAt().toString(),
                lat + "," + lng,
                safeImageReferenceForStorage(rawImageUrl)
        );
        boolean ok = vectorClient.add(chromaId, embedding, metadata);
        if (!ok) {
            log.warn("Failed to index sighting {} in Chroma", saved.getId());
        }
    }

    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    public Page<SightingReviewItemResponse> listReviewSightings(boolean pendingOnly, int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<SightingEntity> entities = pendingOnly
                ? sightingJpaRepository.findByDedupStatusOrderByOccurredAtDesc(DedupStatus.PENDING_REVIEW, pageable)
                : sightingJpaRepository.findAllByOrderByOccurredAtDesc(pageable);
        return entities.map(this::toReviewItem);
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

        return 0.25 * coat + 0.20 * pattern + 0.15 * size + 0.30 * features + 0.10 * ear;
    }

    private SightingReviewItemResponse toReviewItem(SightingEntity s) {
        Double clipDistance = catMatchFeedbackJpaRepository.findFirstBySightingIdOrderByIdDesc(s.getId())
                .map(CatMatchFeedbackEntity::getClipDistance)
                .orElse(null);
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
                clipDistance,
                s.getDedupReason(),
                s.getCreatedAt()
        );
    }

    private record ClipDedupOutcome(ClipDedupDecision decision, float[] embedding) {
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
        u.setId(reporterUserId);
        u.setEmail(email);
        u.setDisplayName("Tracking User " + reporterUserId);
        u.setRole(UserRole.VOLUNTEER);
        u.setCreatedAt(Instant.now());
        try {
            userJpaRepository.save(u);
        } catch (DataIntegrityViolationException ex) {
            if (userJpaRepository.findById(reporterUserId).isEmpty()
                    && userJpaRepository.findByEmail(email).isEmpty()) {
                throw ex;
            }
        }
    }

    private static Instant maxInstant(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
