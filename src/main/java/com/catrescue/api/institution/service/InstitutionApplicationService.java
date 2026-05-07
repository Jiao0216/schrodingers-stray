package com.catrescue.api.institution.service;

import com.catrescue.api.config.InstitutionProperties;
import com.catrescue.api.institution.domain.InstitutionApplicationStatus;
import com.catrescue.api.institution.domain.InstitutionOrgType;
import com.catrescue.api.institution.domain.InstitutionSubscriptionTier;
import com.catrescue.api.institution.dto.ApproveInstitutionApplicationRequest;
import com.catrescue.api.institution.dto.ApproveInstitutionApplicationResponse;
import com.catrescue.api.institution.dto.SubmitInstitutionApplicationRequest;
import com.catrescue.api.institution.persistence.InstitutionApplicationEntity;
import com.catrescue.api.institution.persistence.OrganizationEntity;
import com.catrescue.api.institution.repository.InstitutionApplicationJpaRepository;
import com.catrescue.api.institution.repository.OrganizationJpaRepository;
import com.catrescue.api.institution.support.InstitutionTokenHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class InstitutionApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InstitutionApplicationJpaRepository applicationJpaRepository;
    private final OrganizationJpaRepository organizationJpaRepository;
    private final InstitutionProperties institutionProperties;

    public InstitutionApplicationService(
            InstitutionApplicationJpaRepository applicationJpaRepository,
            OrganizationJpaRepository organizationJpaRepository,
            InstitutionProperties institutionProperties
    ) {
        this.applicationJpaRepository = applicationJpaRepository;
        this.organizationJpaRepository = organizationJpaRepository;
        this.institutionProperties = institutionProperties;
    }

    @Transactional
    public InstitutionApplicationEntity submit(SubmitInstitutionApplicationRequest req) {
        InstitutionApplicationEntity e = new InstitutionApplicationEntity();
        e.setOrganizationName(req.organizationName().trim());
        e.setContactEmail(req.contactEmail().trim().toLowerCase(Locale.ROOT));
        e.setContactName(req.contactName() != null ? req.contactName().trim() : null);
        e.setOrgType(req.orgType() != null ? req.orgType() : InstitutionOrgType.OTHER);
        e.setMissionNote(req.missionNote());
        e.setProposedMinLat(req.proposedMinLat());
        e.setProposedMaxLat(req.proposedMaxLat());
        e.setProposedMinLng(req.proposedMinLng());
        e.setProposedMaxLng(req.proposedMaxLng());
        e.setStatus(InstitutionApplicationStatus.PENDING);
        return applicationJpaRepository.save(e);
    }

    public List<InstitutionApplicationEntity> listPending() {
        return applicationJpaRepository.findByStatusOrderByCreatedAtDesc(InstitutionApplicationStatus.PENDING);
    }

    @Transactional
    public ApproveInstitutionApplicationResponse approve(long applicationId, ApproveInstitutionApplicationRequest req) {
        InstitutionApplicationEntity app = applicationJpaRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        if (app.getStatus() != InstitutionApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "application not pending");
        }
        double minLat;
        double maxLat;
        double minLng;
        double maxLng;
        if (req != null && req.territoryMinLat() != null && req.territoryMaxLat() != null
                && req.territoryMinLng() != null && req.territoryMaxLng() != null) {
            minLat = req.territoryMinLat();
            maxLat = req.territoryMaxLat();
            minLng = req.territoryMinLng();
            maxLng = req.territoryMaxLng();
        } else if (app.getProposedMinLat() != null && app.getProposedMaxLat() != null
                && app.getProposedMinLng() != null && app.getProposedMaxLng() != null) {
            minLat = app.getProposedMinLat();
            maxLat = app.getProposedMaxLat();
            minLng = app.getProposedMinLng();
            maxLng = app.getProposedMaxLng();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "territory bbox required on approve or application");
        }
        validateBbox(minLat, maxLat, minLng, maxLng);

        String publicId = randomPublicId();
        String secret = randomHex(32);
        String fullToken = publicId + "." + secret;
        String pepper = institutionProperties.getTokenPepper() != null ? institutionProperties.getTokenPepper() : "";
        String hash = InstitutionTokenHasher.sha256Hex(pepper + fullToken);

        OrganizationEntity org = new OrganizationEntity();
        org.setName(app.getOrganizationName());
        org.setOrgType(app.getOrgType());
        org.setSubscriptionTier(req != null && req.subscriptionTier() != null
                ? req.subscriptionTier()
                : InstitutionSubscriptionTier.TRIAL);
        org.setSubscriptionExpiresAt(req != null ? req.subscriptionExpiresAt() : null);
        org.setTerritoryMinLat(minLat);
        org.setTerritoryMaxLat(maxLat);
        org.setTerritoryMinLng(minLng);
        org.setTerritoryMaxLng(maxLng);
        org.setApiPublicId(publicId);
        org.setApiSecretHash(hash);
        organizationJpaRepository.save(org);

        app.setStatus(InstitutionApplicationStatus.APPROVED);
        app.setApprovedOrganizationId(org.getId());
        if (req != null && req.adminNote() != null && !req.adminNote().isBlank()) {
            app.setAdminNote(req.adminNote().trim());
        }
        applicationJpaRepository.save(app);

        return new ApproveInstitutionApplicationResponse(org.getId(), fullToken, publicId);
    }

    @Transactional
    public void reject(long applicationId, String note) {
        InstitutionApplicationEntity app = applicationJpaRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        if (app.getStatus() != InstitutionApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "application not pending");
        }
        app.setStatus(InstitutionApplicationStatus.REJECTED);
        app.setAdminNote(note != null ? note.trim() : null);
        applicationJpaRepository.save(app);
    }

    private static void validateBbox(double minLat, double maxLat, double minLng, double maxLng) {
        if (minLat >= maxLat || minLng >= maxLng) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid bbox");
        }
        if (minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox out of range");
        }
    }

    private static String randomPublicId() {
        byte[] buf = new byte[8];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String randomHex(int byteLen) {
        byte[] buf = new byte[byteLen];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
