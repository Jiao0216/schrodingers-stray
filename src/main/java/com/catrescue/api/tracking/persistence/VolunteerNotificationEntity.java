package com.catrescue.api.tracking.persistence;

import com.catrescue.api.tracking.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent in-app message for a volunteer. External delivery (email/SMS) is triggered
 * through ExternalNotificationDispatcher as a hook for email/SMS adapters.
 */
@Entity
@Table(
        name = "volunteer_notifications",
        indexes = {
                @Index(name = "idx_vol_notif_user_time", columnList = "volunteerUserId,createdAt"),
                @Index(name = "idx_vol_notif_sighting", columnList = "relatedSightingId")
        }
)
public class VolunteerNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Recipient volunteer user id. */
    @Column(nullable = false)
    private Long volunteerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Optional JSON payload for future mobile/web clients (e.g. deep links). */
    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @Column
    private Long relatedCatId;

    @Column
    private Long relatedSightingId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVolunteerUserId() {
        return volunteerUserId;
    }

    public void setVolunteerUserId(Long volunteerUserId) {
        this.volunteerUserId = volunteerUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public Long getRelatedCatId() {
        return relatedCatId;
    }

    public void setRelatedCatId(Long relatedCatId) {
        this.relatedCatId = relatedCatId;
    }

    public Long getRelatedSightingId() {
        return relatedSightingId;
    }

    public void setRelatedSightingId(Long relatedSightingId) {
        this.relatedSightingId = relatedSightingId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
