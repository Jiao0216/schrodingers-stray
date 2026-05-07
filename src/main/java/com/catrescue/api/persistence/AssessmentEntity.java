package com.catrescue.api.persistence;

import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.BranchType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessments")
public class AssessmentEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssessmentStatus status;

    @Column(length = 512)
    private String originalFilename;

    @Column(length = 128)
    private String contentType;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BranchType branchType;

    @Column(nullable = false)
    private double sickOrInjuredConfidence;

    @Column(nullable = false)
    private double needsFeedingConfidence;

    @Column(nullable = false)
    private double likelyNotNeuteredConfidence;

    @Column(nullable = false)
    private boolean likelyEarTipped;

    /** Vision body-condition scores 0–100; {@code null} = unknown (pre-migration rows). */
    private Integer healthBodyNormalPercent;

    private Integer healthUndernutritionPercent;

    private Integer healthSeverelyEmaciatedPercent;

    private Integer healthSuspectedInjuryPercent;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> rationalePhrases;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private boolean imageStored;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] imageBytes;

    @Column(length = 512)
    private String addressText;

    @Column(length = 64)
    private String featureCoatColor;

    @Column(length = 64)
    private String featurePatternType;

    @Column(length = 32)
    private String featureBodySize;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> featureSpecialFeatures;

    @Column(nullable = false)
    private boolean featureEarTipped;

    private UUID duplicateOfAssessmentId;

    private Double duplicateSimilarityScore;

    private UUID identityClusterId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BranchType getBranchType() {
        return branchType;
    }

    public void setBranchType(BranchType branchType) {
        this.branchType = branchType;
    }

    public double getSickOrInjuredConfidence() {
        return sickOrInjuredConfidence;
    }

    public void setSickOrInjuredConfidence(double sickOrInjuredConfidence) {
        this.sickOrInjuredConfidence = sickOrInjuredConfidence;
    }

    public double getNeedsFeedingConfidence() {
        return needsFeedingConfidence;
    }

    public void setNeedsFeedingConfidence(double needsFeedingConfidence) {
        this.needsFeedingConfidence = needsFeedingConfidence;
    }

    public double getLikelyNotNeuteredConfidence() {
        return likelyNotNeuteredConfidence;
    }

    public void setLikelyNotNeuteredConfidence(double likelyNotNeuteredConfidence) {
        this.likelyNotNeuteredConfidence = likelyNotNeuteredConfidence;
    }

    public boolean isLikelyEarTipped() {
        return likelyEarTipped;
    }

    public void setLikelyEarTipped(boolean likelyEarTipped) {
        this.likelyEarTipped = likelyEarTipped;
    }

    public Integer getHealthBodyNormalPercent() {
        return healthBodyNormalPercent;
    }

    public void setHealthBodyNormalPercent(Integer healthBodyNormalPercent) {
        this.healthBodyNormalPercent = healthBodyNormalPercent;
    }

    public Integer getHealthUndernutritionPercent() {
        return healthUndernutritionPercent;
    }

    public void setHealthUndernutritionPercent(Integer healthUndernutritionPercent) {
        this.healthUndernutritionPercent = healthUndernutritionPercent;
    }

    public Integer getHealthSeverelyEmaciatedPercent() {
        return healthSeverelyEmaciatedPercent;
    }

    public void setHealthSeverelyEmaciatedPercent(Integer healthSeverelyEmaciatedPercent) {
        this.healthSeverelyEmaciatedPercent = healthSeverelyEmaciatedPercent;
    }

    public Integer getHealthSuspectedInjuryPercent() {
        return healthSuspectedInjuryPercent;
    }

    public void setHealthSuspectedInjuryPercent(Integer healthSuspectedInjuryPercent) {
        this.healthSuspectedInjuryPercent = healthSuspectedInjuryPercent;
    }

    public List<String> getRationalePhrases() {
        return rationalePhrases;
    }

    public void setRationalePhrases(List<String> rationalePhrases) {
        this.rationalePhrases = rationalePhrases;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isImageStored() {
        return imageStored;
    }

    public void setImageStored(boolean imageStored) {
        this.imageStored = imageStored;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAddressText() {
        return addressText;
    }

    public void setAddressText(String addressText) {
        this.addressText = addressText;
    }

    public String getFeatureCoatColor() {
        return featureCoatColor;
    }

    public void setFeatureCoatColor(String featureCoatColor) {
        this.featureCoatColor = featureCoatColor;
    }

    public String getFeaturePatternType() {
        return featurePatternType;
    }

    public void setFeaturePatternType(String featurePatternType) {
        this.featurePatternType = featurePatternType;
    }

    public String getFeatureBodySize() {
        return featureBodySize;
    }

    public void setFeatureBodySize(String featureBodySize) {
        this.featureBodySize = featureBodySize;
    }

    public List<String> getFeatureSpecialFeatures() {
        return featureSpecialFeatures;
    }

    public void setFeatureSpecialFeatures(List<String> featureSpecialFeatures) {
        this.featureSpecialFeatures = featureSpecialFeatures;
    }

    public boolean isFeatureEarTipped() {
        return featureEarTipped;
    }

    public void setFeatureEarTipped(boolean featureEarTipped) {
        this.featureEarTipped = featureEarTipped;
    }

    public UUID getDuplicateOfAssessmentId() {
        return duplicateOfAssessmentId;
    }

    public void setDuplicateOfAssessmentId(UUID duplicateOfAssessmentId) {
        this.duplicateOfAssessmentId = duplicateOfAssessmentId;
    }

    public Double getDuplicateSimilarityScore() {
        return duplicateSimilarityScore;
    }

    public void setDuplicateSimilarityScore(Double duplicateSimilarityScore) {
        this.duplicateSimilarityScore = duplicateSimilarityScore;
    }

    public UUID getIdentityClusterId() {
        return identityClusterId;
    }

    public void setIdentityClusterId(UUID identityClusterId) {
        this.identityClusterId = identityClusterId;
    }
}
