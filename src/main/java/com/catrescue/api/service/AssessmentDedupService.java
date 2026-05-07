package com.catrescue.api.service;

import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.CatFeatureSnapshot;
import com.catrescue.api.persistence.AssessmentEntity;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import com.catrescue.api.tracking.service.CatFeatureSimilarity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds visually similar prior assessments using stored cat feature snapshots.
 */
@Service
public class AssessmentDedupService {

    /** Community duplicate banner threshold (product: 70%). */
    public static final double DUPLICATE_THRESHOLD = 0.70;

    private final AssessmentJpaRepository jpa;

    public AssessmentDedupService(AssessmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Best prior assessment above similarity threshold (excludes {@code excludeId}).
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
                .filter(s -> s.score >= DUPLICATE_THRESHOLD)
                .max(Comparator.comparingDouble((Scored s) -> s.score))
                .map(s -> new ScoredAssessmentMatch(s.entity, s.score));
    }

    public record ScoredAssessmentMatch(AssessmentEntity prior, double score) {
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

    private record Scored(AssessmentEntity entity, double score) {
    }
}
