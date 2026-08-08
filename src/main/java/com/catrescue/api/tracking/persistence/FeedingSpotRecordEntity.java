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
        name = "feeding_spot_records",
        indexes = {
                @Index(name = "idx_fsr_spot_fed", columnList = "feedingSpotId,fedAt")
        }
)
public class FeedingSpotRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long feedingSpotId;

    @Column
    private Long fedByUserId;

    @Column(nullable = false)
    private Instant fedAt;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFeedingSpotId() {
        return feedingSpotId;
    }

    public void setFeedingSpotId(Long feedingSpotId) {
        this.feedingSpotId = feedingSpotId;
    }

    public Long getFedByUserId() {
        return fedByUserId;
    }

    public void setFedByUserId(Long fedByUserId) {
        this.fedByUserId = fedByUserId;
    }

    public Instant getFedAt() {
        return fedAt;
    }

    public void setFedAt(Instant fedAt) {
        this.fedAt = fedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
