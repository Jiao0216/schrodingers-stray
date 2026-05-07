package com.catrescue.api.tracking.persistence;

import com.catrescue.api.tracking.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 40)
    private String phone;

    /** BCrypt hash for email/password login; null for legacy or bot-created users without credentials. */
    @Column(length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.VOLUNTEER;

    /**
     * Optional coordinates where this volunteer wants proximity alerts (500 m radius from new sightings).
     * When null, the user will not receive UNNEUTERED_SIGHTING_NEARBY pushes by location.
     */
    @Column
    private Double serviceLatitude;

    @Column
    private Double serviceLongitude;

    /** Short bio shown on the volunteer profile (C-end). */
    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Gamification points from feeding check-ins (and future rules). Used for leaderboard.
     */
    @Column(nullable = false)
    private long volunteerPoints;

    /**
     * When false, skip proximity-based in-app alerts for this volunteer (still receives absence reminders tied to their sightings).
     */
    @Column(nullable = false)
    private boolean notifyNearbyEnabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Double getServiceLatitude() {
        return serviceLatitude;
    }

    public void setServiceLatitude(Double serviceLatitude) {
        this.serviceLatitude = serviceLatitude;
    }

    public Double getServiceLongitude() {
        return serviceLongitude;
    }

    public void setServiceLongitude(Double serviceLongitude) {
        this.serviceLongitude = serviceLongitude;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public long getVolunteerPoints() {
        return volunteerPoints;
    }

    public void setVolunteerPoints(long volunteerPoints) {
        this.volunteerPoints = volunteerPoints;
    }

    public boolean isNotifyNearbyEnabled() {
        return notifyNearbyEnabled;
    }

    public void setNotifyNearbyEnabled(boolean notifyNearbyEnabled) {
        this.notifyNearbyEnabled = notifyNearbyEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
