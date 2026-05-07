package com.catrescue.api.tracking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Community feeding station / dispenser (demo). Columns: id, name, lat, lng, last_fed_at.
 * Optional second line in {@code name} (after {@code \n}) is treated as street address in API responses.
 */
@Entity
@Table(name = "feeding_stations")
public class FeedingStationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 600)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column
    private Instant lastFedAt;

    /**
     * Optional link to a B2B {@link com.catrescue.api.institution.persistence.OrganizationEntity} for custody / ops.
     */
    @Column
    private Long managedByOrganizationId;

    @Column(length = 2000)
    private String partnerNotes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Instant getLastFedAt() {
        return lastFedAt;
    }

    public void setLastFedAt(Instant lastFedAt) {
        this.lastFedAt = lastFedAt;
    }

    public Long getManagedByOrganizationId() {
        return managedByOrganizationId;
    }

    public void setManagedByOrganizationId(Long managedByOrganizationId) {
        this.managedByOrganizationId = managedByOrganizationId;
    }

    public String getPartnerNotes() {
        return partnerNotes;
    }

    public void setPartnerNotes(String partnerNotes) {
        this.partnerNotes = partnerNotes;
    }
}
