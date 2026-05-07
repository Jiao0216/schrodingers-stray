package com.catrescue.api.tracking.persistence;

import com.catrescue.api.tracking.domain.VolunteerBadgeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "volunteer_badges_earned",
        uniqueConstraints = @UniqueConstraint(name = "uk_vol_badge_user_code", columnNames = {"user_id", "badge_code"}),
        indexes = {@Index(name = "idx_vol_badge_user", columnList = "user_id")}
)
public class VolunteerBadgeEarnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_code", nullable = false, length = 40)
    private VolunteerBadgeCode badgeCode;

    @Column(nullable = false)
    private Instant earnedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public VolunteerBadgeCode getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(VolunteerBadgeCode badgeCode) {
        this.badgeCode = badgeCode;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(Instant earnedAt) {
        this.earnedAt = earnedAt;
    }
}
