package com.catrescue.api.institution.persistence;

import com.catrescue.api.institution.domain.InstitutionApplicationStatus;
import com.catrescue.api.institution.domain.InstitutionOrgType;
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
@Table(name = "institution_applications")
public class InstitutionApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String organizationName;

    @Column(nullable = false, length = 255)
    private String contactEmail;

    @Column(length = 120)
    private String contactName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstitutionOrgType orgType = InstitutionOrgType.OTHER;

    @Column(length = 2000)
    private String missionNote;

    private Double proposedMinLat;
    private Double proposedMaxLat;
    private Double proposedMinLng;
    private Double proposedMaxLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstitutionApplicationStatus status = InstitutionApplicationStatus.PENDING;

    @Column(length = 2000)
    private String adminNote;

    @Column
    private Long approvedOrganizationId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public InstitutionOrgType getOrgType() {
        return orgType;
    }

    public void setOrgType(InstitutionOrgType orgType) {
        this.orgType = orgType;
    }

    public String getMissionNote() {
        return missionNote;
    }

    public void setMissionNote(String missionNote) {
        this.missionNote = missionNote;
    }

    public Double getProposedMinLat() {
        return proposedMinLat;
    }

    public void setProposedMinLat(Double proposedMinLat) {
        this.proposedMinLat = proposedMinLat;
    }

    public Double getProposedMaxLat() {
        return proposedMaxLat;
    }

    public void setProposedMaxLat(Double proposedMaxLat) {
        this.proposedMaxLat = proposedMaxLat;
    }

    public Double getProposedMinLng() {
        return proposedMinLng;
    }

    public void setProposedMinLng(Double proposedMinLng) {
        this.proposedMinLng = proposedMinLng;
    }

    public Double getProposedMaxLng() {
        return proposedMaxLng;
    }

    public void setProposedMaxLng(Double proposedMaxLng) {
        this.proposedMaxLng = proposedMaxLng;
    }

    public InstitutionApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(InstitutionApplicationStatus status) {
        this.status = status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public Long getApprovedOrganizationId() {
        return approvedOrganizationId;
    }

    public void setApprovedOrganizationId(Long approvedOrganizationId) {
        this.approvedOrganizationId = approvedOrganizationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
