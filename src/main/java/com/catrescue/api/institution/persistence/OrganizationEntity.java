package com.catrescue.api.institution.persistence;

import com.catrescue.api.institution.domain.InstitutionOrgType;
import com.catrescue.api.institution.domain.InstitutionSubscriptionTier;
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

@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(name = "idx_org_api_public_id", columnList = "apiPublicId", unique = true)
        }
)
public class OrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstitutionOrgType orgType = InstitutionOrgType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstitutionSubscriptionTier subscriptionTier = InstitutionSubscriptionTier.TRIAL;

    @Column
    private Instant subscriptionExpiresAt;

    /** Bounding box for jurisdiction (WGS84). */
    @Column(nullable = false)
    private double territoryMinLat;

    @Column(nullable = false)
    private double territoryMaxLat;

    @Column(nullable = false)
    private double territoryMinLng;

    @Column(nullable = false)
    private double territoryMaxLng;

    /**
     * Public id embedded in API tokens ({@code public.secret}).
     */
    @Column(nullable = false, unique = true, length = 16)
    private String apiPublicId;

    /**
     * Hex SHA-256 of {@code pepper + fullToken}, see {@link com.catrescue.api.institution.support.InstitutionTokenHasher}.
     */
    @Column(nullable = false, length = 64)
    private String apiSecretHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

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

    public InstitutionOrgType getOrgType() {
        return orgType;
    }

    public void setOrgType(InstitutionOrgType orgType) {
        this.orgType = orgType;
    }

    public InstitutionSubscriptionTier getSubscriptionTier() {
        return subscriptionTier;
    }

    public void setSubscriptionTier(InstitutionSubscriptionTier subscriptionTier) {
        this.subscriptionTier = subscriptionTier;
    }

    public Instant getSubscriptionExpiresAt() {
        return subscriptionExpiresAt;
    }

    public void setSubscriptionExpiresAt(Instant subscriptionExpiresAt) {
        this.subscriptionExpiresAt = subscriptionExpiresAt;
    }

    public double getTerritoryMinLat() {
        return territoryMinLat;
    }

    public void setTerritoryMinLat(double territoryMinLat) {
        this.territoryMinLat = territoryMinLat;
    }

    public double getTerritoryMaxLat() {
        return territoryMaxLat;
    }

    public void setTerritoryMaxLat(double territoryMaxLat) {
        this.territoryMaxLat = territoryMaxLat;
    }

    public double getTerritoryMinLng() {
        return territoryMinLng;
    }

    public void setTerritoryMinLng(double territoryMinLng) {
        this.territoryMinLng = territoryMinLng;
    }

    public double getTerritoryMaxLng() {
        return territoryMaxLng;
    }

    public void setTerritoryMaxLng(double territoryMaxLng) {
        this.territoryMaxLng = territoryMaxLng;
    }

    public String getApiPublicId() {
        return apiPublicId;
    }

    public void setApiPublicId(String apiPublicId) {
        this.apiPublicId = apiPublicId;
    }

    public String getApiSecretHash() {
        return apiSecretHash;
    }

    public void setApiSecretHash(String apiSecretHash) {
        this.apiSecretHash = apiSecretHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
