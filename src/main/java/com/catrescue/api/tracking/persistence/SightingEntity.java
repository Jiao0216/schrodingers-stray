package com.catrescue.api.tracking.persistence;

import com.catrescue.api.persistence.StringListJsonConverter;
import com.catrescue.api.tracking.domain.DedupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(
        name = "sightings",
        indexes = {
                @Index(name = "idx_sightings_time_coords", columnList = "occurredAt,latitude,longitude"),
                @Index(name = "idx_sightings_cat", columnList = "catId"),
                @Index(name = "idx_sightings_dedup", columnList = "dedupStatus,suggestedCatId,occurredAt"),
                @Index(name = "idx_sightings_reporter", columnList = "reporterUserId,occurredAt")
        }
)
public class SightingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long catId;

    @Column(nullable = false)
    private Long reporterUserId;

    /**
     * May store remote URL or data URL (base64) when uploaded directly.
     * Use MEDIUMTEXT to avoid truncation for larger images.
     */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String imageUrl;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] imageBytes;

    @Column(length = 120)
    private String imageContentType;

    @Column(length = 600)
    private String addressText;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 40)
    private String coatColor;

    @Column(nullable = false, length = 40)
    private String patternType;

    @Column(nullable = false, length = 20)
    private String bodySize;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> specialFeatures;

    @Column(nullable = false)
    private boolean earTipped;

    @Column(nullable = false)
    private double earTippedConfidence;

    @Column(nullable = false)
    private double featureExtractionConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DedupStatus dedupStatus;

    @Column
    private Long suggestedCatId;

    @Column
    private Long duplicateOfSightingId;

    @Column
    private Double similarityScore;

    @Column(columnDefinition = "TEXT")
    private String dedupReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCatId() {
        return catId;
    }

    public void setCatId(Long catId) {
        this.catId = catId;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(Long reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }

    public String getAddressText() {
        return addressText;
    }

    public void setAddressText(String addressText) {
        this.addressText = addressText;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCoatColor() {
        return coatColor;
    }

    public void setCoatColor(String coatColor) {
        this.coatColor = coatColor;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }

    public String getBodySize() {
        return bodySize;
    }

    public void setBodySize(String bodySize) {
        this.bodySize = bodySize;
    }

    public List<String> getSpecialFeatures() {
        return specialFeatures;
    }

    public void setSpecialFeatures(List<String> specialFeatures) {
        this.specialFeatures = specialFeatures;
    }

    public boolean isEarTipped() {
        return earTipped;
    }

    public void setEarTipped(boolean earTipped) {
        this.earTipped = earTipped;
    }

    public double getEarTippedConfidence() {
        return earTippedConfidence;
    }

    public void setEarTippedConfidence(double earTippedConfidence) {
        this.earTippedConfidence = earTippedConfidence;
    }

    public double getFeatureExtractionConfidence() {
        return featureExtractionConfidence;
    }

    public void setFeatureExtractionConfidence(double featureExtractionConfidence) {
        this.featureExtractionConfidence = featureExtractionConfidence;
    }

    public DedupStatus getDedupStatus() {
        return dedupStatus;
    }

    public void setDedupStatus(DedupStatus dedupStatus) {
        this.dedupStatus = dedupStatus;
    }

    public Long getSuggestedCatId() {
        return suggestedCatId;
    }

    public void setSuggestedCatId(Long suggestedCatId) {
        this.suggestedCatId = suggestedCatId;
    }

    public Long getDuplicateOfSightingId() {
        return duplicateOfSightingId;
    }

    public void setDuplicateOfSightingId(Long duplicateOfSightingId) {
        this.duplicateOfSightingId = duplicateOfSightingId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getDedupReason() {
        return dedupReason;
    }

    public void setDedupReason(String dedupReason) {
        this.dedupReason = dedupReason;
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
}
