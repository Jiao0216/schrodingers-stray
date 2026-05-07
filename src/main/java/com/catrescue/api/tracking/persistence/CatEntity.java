package com.catrescue.api.tracking.persistence;

import com.catrescue.api.persistence.StringListJsonConverter;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(
        name = "cats",
        indexes = {
                @Index(name = "idx_cats_last_seen", columnList = "lastSeenAt"),
                @Index(name = "idx_cats_last_coords", columnList = "lastSeenLat,lastSeenLng"),
                @Index(name = "idx_cats_sterilized", columnList = "sterilizationStatus,earTipped")
        }
)
public class CatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String displayName;

    @Column(length = 40)
    private String coatColor;

    @Column(length = 40)
    private String patternType;

    @Column(length = 20)
    private String bodySize;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> specialFeatures;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SterilizationStatus sterilizationStatus = SterilizationStatus.UNKNOWN;

    @Column(nullable = false)
    private boolean earTipped = false;

    @Column
    private Double sterilizationConfidence;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private Double lastSeenLat;

    @Column(nullable = false)
    private Double lastSeenLng;

    @Column
    private Long createdByUserId;

    /**
     * When set, an absence reminder was already sent for the current lastSeenAt streak.
     * Cleared whenever lastSeenAt is advanced by a new merged sighting.
     */
    @Column
    private Instant absenceReminderSentAt;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public SterilizationStatus getSterilizationStatus() {
        return sterilizationStatus;
    }

    public void setSterilizationStatus(SterilizationStatus sterilizationStatus) {
        this.sterilizationStatus = sterilizationStatus;
    }

    public boolean isEarTipped() {
        return earTipped;
    }

    public void setEarTipped(boolean earTipped) {
        this.earTipped = earTipped;
    }

    public Double getSterilizationConfidence() {
        return sterilizationConfidence;
    }

    public void setSterilizationConfidence(Double sterilizationConfidence) {
        this.sterilizationConfidence = sterilizationConfidence;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Double getLastSeenLat() {
        return lastSeenLat;
    }

    public void setLastSeenLat(Double lastSeenLat) {
        this.lastSeenLat = lastSeenLat;
    }

    public Double getLastSeenLng() {
        return lastSeenLng;
    }

    public void setLastSeenLng(Double lastSeenLng) {
        this.lastSeenLng = lastSeenLng;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Instant getAbsenceReminderSentAt() {
        return absenceReminderSentAt;
    }

    public void setAbsenceReminderSentAt(Instant absenceReminderSentAt) {
        this.absenceReminderSentAt = absenceReminderSentAt;
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
