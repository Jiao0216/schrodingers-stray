package com.catrescue.api.repository;

import com.catrescue.api.domain.Assessment;
import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.BranchType;
import com.catrescue.api.domain.CatFeatureSnapshot;
import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.dto.StoredAssessmentImage;
import com.catrescue.api.persistence.AssessmentEntity;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DbAssessmentRepository implements AssessmentRepository {

    private final AssessmentJpaRepository jpa;

    public DbAssessmentRepository(AssessmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Assessment save(Assessment assessment, byte[] optionalStoredImageBytes) {
        AssessmentEntity e = toEntity(assessment);
        if (optionalStoredImageBytes != null && optionalStoredImageBytes.length > 0) {
            e.setImageBytes(optionalStoredImageBytes);
            e.setImageStored(true);
        } else {
            e.setImageBytes(null);
            e.setImageStored(false);
        }
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Assessment> findById(UUID id) {
        return jpa.findById(id).map(DbAssessmentRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredAssessmentImage> findStoredImage(UUID id) {
        return jpa.findById(id)
                .filter(AssessmentEntity::isImageStored)
                .map(e -> new StoredAssessmentImage(
                        e.getImageBytes(),
                        e.getContentType() != null && !e.getContentType().isBlank()
                                ? e.getContentType()
                                : "application/octet-stream"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findTop500ByCreatedAtDesc() {
        return jpa.findTop500ByOrderByCreatedAtDesc().stream()
                .map(DbAssessmentRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findAllByOrderByCreatedAtDesc() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findAll() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findAll(Sort sort) {
        return jpa.findAll(sort).stream()
                .map(DbAssessmentRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> findByIdentityClusterIdOrdered(UUID identityClusterId) {
        if (identityClusterId == null) {
            return List.of();
        }
        return jpa.findByIdentityClusterIdOrderByCreatedAtAsc(identityClusterId).stream()
                .map(DbAssessmentRepository::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByIdentityClusterId(UUID identityClusterId) {
        if (identityClusterId == null) {
            return 0;
        }
        return jpa.countByIdentityClusterId(identityClusterId);
    }

    private static AssessmentEntity toEntity(Assessment a) {
        AssessmentEntity e = new AssessmentEntity();
        e.setId(a.id());
        e.setStatus(a.status());
        e.setOriginalFilename(a.originalFilename());
        e.setContentType(a.contentType());
        e.setLatitude(a.latitude());
        e.setLongitude(a.longitude());
        e.setBranchType(a.branchType());
        e.setSickOrInjuredConfidence(a.modelLabels().sickOrInjuredConfidence());
        e.setNeedsFeedingConfidence(a.modelLabels().needsFeedingConfidence());
        e.setLikelyNotNeuteredConfidence(a.modelLabels().likelyNotNeuteredOrEarNotTippedConfidence());
        e.setLikelyEarTipped(a.modelLabels().likelyEarTipped());
        e.setHealthBodyNormalPercent(pctOrNull(a.modelLabels().healthBodyNormalPercent()));
        e.setHealthUndernutritionPercent(pctOrNull(a.modelLabels().healthUndernutritionPercent()));
        e.setHealthSeverelyEmaciatedPercent(pctOrNull(a.modelLabels().healthSeverelyEmaciatedPercent()));
        e.setHealthSuspectedInjuryPercent(pctOrNull(a.modelLabels().healthSuspectedInjuryPercent()));
        e.setRationalePhrases(a.modelLabels().rationalePhrases() != null
                ? a.modelLabels().rationalePhrases()
                : List.of());
        e.setFailureReason(a.failureReason());
        e.setCreatedAt(a.createdAt());
        e.setUpdatedAt(a.updatedAt());
        e.setAddressText(a.addressText());
        applyFeatures(e, a.catFeatures());
        e.setDuplicateOfAssessmentId(a.duplicateOfAssessmentId());
        e.setDuplicateSimilarityScore(a.duplicateSimilarityScore());
        e.setIdentityClusterId(a.identityClusterId());
        return e;
    }

    private static void applyFeatures(AssessmentEntity e, CatFeatureSnapshot f) {
        if (f == null) {
            e.setFeatureCoatColor(null);
            e.setFeaturePatternType(null);
            e.setFeatureBodySize(null);
            e.setFeatureSpecialFeatures(List.of());
            e.setFeatureEarTipped(false);
            return;
        }
        e.setFeatureCoatColor(f.coatColor());
        e.setFeaturePatternType(f.patternType());
        e.setFeatureBodySize(f.bodySize());
        e.setFeatureSpecialFeatures(f.specialFeatures() != null ? f.specialFeatures() : List.of());
        e.setFeatureEarTipped(f.earTipped());
    }

    private static Integer pctOrNull(int v) {
        return v < 0 ? null : v;
    }

    private static int pctFromDb(Integer v) {
        return v == null ? -1 : v;
    }

    private static Assessment toDomain(AssessmentEntity e) {
        ModelLabels labels = new ModelLabels(
                e.getSickOrInjuredConfidence(),
                e.getNeedsFeedingConfidence(),
                e.getLikelyNotNeuteredConfidence(),
                e.getRationalePhrases() != null ? e.getRationalePhrases() : List.of(),
                e.isLikelyEarTipped(),
                false,
                "",
                "medium",
                false,
                false,
                -1,
                "",
                pctFromDb(e.getHealthBodyNormalPercent()),
                pctFromDb(e.getHealthUndernutritionPercent()),
                pctFromDb(e.getHealthSeverelyEmaciatedPercent()),
                pctFromDb(e.getHealthSuspectedInjuryPercent())
        );
        CatFeatureSnapshot features = catFeaturesFromEntity(e);
        return new Assessment(
                e.getId(),
                e.getStatus(),
                e.getOriginalFilename(),
                e.getContentType(),
                e.getLatitude(),
                e.getLongitude(),
                e.getBranchType(),
                labels,
                e.getFailureReason(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.isImageStored(),
                e.getAddressText(),
                features,
                e.getDuplicateOfAssessmentId(),
                e.getDuplicateSimilarityScore(),
                e.getIdentityClusterId()
        );
    }

    private static CatFeatureSnapshot catFeaturesFromEntity(AssessmentEntity e) {
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
