package com.catrescue.api.institution.web;

import com.catrescue.api.institution.dto.ApproveInstitutionApplicationRequest;
import com.catrescue.api.institution.dto.ApproveInstitutionApplicationResponse;
import com.catrescue.api.institution.dto.InstitutionApplicationViewDto;
import com.catrescue.api.institution.dto.RejectInstitutionApplicationRequest;
import com.catrescue.api.institution.persistence.InstitutionApplicationEntity;
import com.catrescue.api.institution.service.InstitutionApplicationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/institution/admin")
public class InstitutionAdminController {

    private final InstitutionApplicationService institutionApplicationService;

    public InstitutionAdminController(InstitutionApplicationService institutionApplicationService) {
        this.institutionApplicationService = institutionApplicationService;
    }

    @GetMapping("/applications")
    public List<InstitutionApplicationViewDto> listPending() {
        return institutionApplicationService.listPending().stream()
                .map(InstitutionAdminController::toView)
                .toList();
    }

    @PostMapping("/applications/{id}/approve")
    public ApproveInstitutionApplicationResponse approve(
            @PathVariable("id") long id,
            @RequestBody(required = false) ApproveInstitutionApplicationRequest body
    ) {
        return institutionApplicationService.approve(id, body);
    }

    @PostMapping("/applications/{id}/reject")
    public void reject(
            @PathVariable("id") long id,
            @RequestBody(required = false) RejectInstitutionApplicationRequest body
    ) {
        institutionApplicationService.reject(id, body != null ? body.adminNote() : null);
    }

    private static InstitutionApplicationViewDto toView(InstitutionApplicationEntity e) {
        return new InstitutionApplicationViewDto(
                e.getId(),
                e.getOrganizationName(),
                e.getContactEmail(),
                e.getContactName(),
                e.getOrgType(),
                e.getMissionNote(),
                e.getProposedMinLat(),
                e.getProposedMaxLat(),
                e.getProposedMinLng(),
                e.getProposedMaxLng(),
                e.getStatus(),
                e.getAdminNote(),
                e.getApprovedOrganizationId(),
                e.getCreatedAt()
        );
    }
}
