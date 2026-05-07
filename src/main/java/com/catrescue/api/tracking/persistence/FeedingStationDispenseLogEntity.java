package com.catrescue.api.tracking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One automatic (scheduled) or manual dispense event for dashboard metrics and audit.
 */
@Entity
@Table(
        name = "feeding_station_dispense_logs",
        indexes = {
                @Index(name = "idx_dispense_at", columnList = "dispensedAt"),
                @Index(name = "idx_dispense_sched", columnList = "scheduled,dispensedAt")
        }
)
public class FeedingStationDispenseLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long feedingStationId;

    @Column(nullable = false)
    private Instant dispensedAt;

    /**
     * {@code true} for cron-triggered daily feeds (counted in “today auto dispenses” KPI).
     */
    @Column(nullable = false)
    private boolean scheduled;

    @Column(length = 32)
    private String slot;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFeedingStationId() {
        return feedingStationId;
    }

    public void setFeedingStationId(Long feedingStationId) {
        this.feedingStationId = feedingStationId;
    }

    public Instant getDispensedAt() {
        return dispensedAt;
    }

    public void setDispensedAt(Instant dispensedAt) {
        this.dispensedAt = dispensedAt;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }
}
