package com.catrescue.api.tracking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "cat_match_feedback",
        indexes = {
                @Index(name = "idx_cmf_sighting", columnList = "sightingId"),
                @Index(name = "idx_cmf_confirmed", columnList = "volunteerConfirmed,confirmedAt")
        }
)
public class CatMatchFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sightingId;

    @Column
    private Long matchedSightingId;

    @Column
    private Long matchedCatId;

    @Column
    private Double clipDistance;

    @Column
    private Double featureSimilarity;

    /** null = pending review; true = confirmed same cat; false = rejected. */
    @Column
    private Boolean volunteerConfirmed;

    @Column
    private Instant confirmedAt;

    @Column(length = 120)
    private String confirmedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSightingId() {
        return sightingId;
    }

    public void setSightingId(Long sightingId) {
        this.sightingId = sightingId;
    }

    public Long getMatchedSightingId() {
        return matchedSightingId;
    }

    public void setMatchedSightingId(Long matchedSightingId) {
        this.matchedSightingId = matchedSightingId;
    }

    public Long getMatchedCatId() {
        return matchedCatId;
    }

    public void setMatchedCatId(Long matchedCatId) {
        this.matchedCatId = matchedCatId;
    }

    public Double getClipDistance() {
        return clipDistance;
    }

    public void setClipDistance(Double clipDistance) {
        this.clipDistance = clipDistance;
    }

    public Double getFeatureSimilarity() {
        return featureSimilarity;
    }

    public void setFeatureSimilarity(Double featureSimilarity) {
        this.featureSimilarity = featureSimilarity;
    }

    public Boolean getVolunteerConfirmed() {
        return volunteerConfirmed;
    }

    public void setVolunteerConfirmed(Boolean volunteerConfirmed) {
        this.volunteerConfirmed = volunteerConfirmed;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
