package com.catrescue.api.service;

import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.CatFeatureSnapshot;
import com.catrescue.api.persistence.AssessmentEntity;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import com.catrescue.api.service.client.ClipEmbeddingClient;
import com.catrescue.api.tracking.service.CatFeatureSimilarity;
import com.catrescue.api.tracking.service.ClipThresholdCalibrationService;
import com.catrescue.api.tracking.vector.VectorClient;
import com.catrescue.api.tracking.vector.VectorMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds prior assessments via OpenAI feature snapshots and/or CLIP + Chroma vectors.
 */
@Service
public class AssessmentDedupService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentDedupService.class);

    /** Community duplicate banner threshold (OpenAI feature blend). */
    public static final double FEATURE_DUPLICATE_THRESHOLD = 0.70;

    private static final int CLIP_QUERY_TOP_K = 5;

    private final AssessmentJpaRepository jpa;
    private final ClipEmbeddingClient clipEmbeddingClient;
    private final VectorClient vectorClient;
    private final ClipThresholdCalibrationService clipThresholdCalibrationService;

    public AssessmentDedupService(
            AssessmentJpaRepository jpa,
            ClipEmbeddingClient clipEmbeddingClient,
            VectorClient vectorClient,
            ClipThresholdCalibrationService clipThresholdCalibrationService
    ) {
        this.jpa = jpa;
        this.clipEmbeddingClient = clipEmbeddingClient;
        this.vectorClient = vectorClient;
        this.clipThresholdCalibrationService = clipThresholdCalibrationService;
    }

    /**
     * Best prior assessment above feature similarity threshold (excludes {@code excludeId}).
     */
    public Optional<ScoredAssessmentMatch> findBestMatchingPriorAssessment(CatFeatureSnapshot incoming, UUID excludeId) {
        if (incoming == null) {
            return Optional.empty();
        }
        List<AssessmentEntity> candidates = jpa.findTop400ByStatusOrderByCreatedAtDesc(AssessmentStatus.COMPLETED);
        return candidates.stream()
                .filter(c -> c.getId() != null && !c.getId().equals(excludeId))
                .filter(c -> snapshotFromEntity(c) != null)
                .map(c -> new Scored(c, CatFeatureSimilarity.similarity(incoming, snapshotFromEntity(c))))
                .filter(s -> s.score >= FEATURE_DUPLICATE_THRESHOLD)
                .max(Comparator.comparingDouble((Scored s) -> s.score))
                .map(s -> new ScoredAssessmentMatch(s.entity, s.score));
    }

    /**
     * CLIP nearest-neighbor match against Chroma (assessment_* and sighting_* vectors).
     */
    public Optional<ClipAssessmentMatch> findClipMatch(byte[] imageBytes, String contentType, UUID excludeId) {
        if (!clipEmbeddingClient.isEnabled() || imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        Optional<float[]> embedding = clipEmbeddingClient.embedBytes(imageBytes, "upload.jpg", contentType);
        if (embedding.isEmpty()) {
            return Optional.empty();
        }

        String excludeChromaId = excludeId != null ? "assessment_" + excludeId : null;
        List<VectorMatchResult> matches = vectorClient.queryTopK(embedding.get(), CLIP_QUERY_TOP_K, excludeChromaId);
        double threshold = clipThresholdCalibrationService.effectiveThreshold();

        for (VectorMatchResult match : matches) {
            if (match.distance() >= threshold) {
                continue;
            }
            Optional<AssessmentEntity> prior = resolveAssessmentFromMatch(match);
            if (prior.isEmpty() || prior.get().getId().equals(excludeId)) {
                continue;
            }
            return Optional.of(new ClipAssessmentMatch(prior.get(), match.distance(), embedding.get()));
        }
        return Optional.empty();
    }

    private Optional<AssessmentEntity> resolveAssessmentFromMatch(VectorMatchResult match) {
        if (match == null) {
            return Optional.empty();
        }
        String chromaId = match.id() != null ? match.id().trim() : "";
        if (chromaId.startsWith("assessment_")) {
            try {
                UUID id = UUID.fromString(chromaId.substring("assessment_".length()));
                return jpa.findById(id);
            } catch (IllegalArgumentException ex) {
                log.debug("Invalid assessment chroma id: {}", chromaId);
            }
        }
        if (chromaId.startsWith("sighting_")) {
            try {
                long sid = Long.parseLong(chromaId.substring("sighting_".length()));
                return jpa.findFirstByTrackingSightingIdOrderByCreatedAtDesc(sid);
            } catch (NumberFormatException ex) {
                log.debug("Invalid sighting chroma id: {}", chromaId);
            }
        }
        if (match.sightingId() > 0) {
            return jpa.findFirstByTrackingSightingIdOrderByCreatedAtDesc(match.sightingId());
        }
        return Optional.empty();
    }

    public record ScoredAssessmentMatch(AssessmentEntity prior, double score) {
    }

    public record ClipAssessmentMatch(AssessmentEntity prior, double clipDistance, float[] embedding) {
    }

    private record Scored(AssessmentEntity entity, double score) {
    }

    private static CatFeatureSnapshot snapshotFromEntity(AssessmentEntity e) {
        if (e.getFeatureCoatColor() == null && e.getFeaturePatternType() == null && e.getFeatureBodySize() == null) {
            return null;
        }
        return new CatFeatureSnapshot(
                e.getFeatureCoatColor(),
                e.getFeaturePatternType(),
                e.getFeatureBodySize(),
                e.getFeatureSpecialFeatures() != null ? e.getFeatureSpecialFeatures() : List.of(),
                e.isFeatureEarTipped()
        );
    }
}
