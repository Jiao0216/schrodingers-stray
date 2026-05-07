package com.catrescue.api.institution.web;

import com.catrescue.api.institution.dto.AddPartnerVolunteerRequest;
import com.catrescue.api.institution.dto.CatPriorityDto;
import com.catrescue.api.institution.dto.InstitutionPortalProfileDto;
import com.catrescue.api.institution.dto.PartnerVolunteerDto;
import com.catrescue.api.institution.dto.PatchFeedingStationRequest;
import com.catrescue.api.institution.persistence.OrganizationEntity;
import com.catrescue.api.institution.service.InstitutionPortalService;
import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/institution/portal")
public class InstitutionPortalController {

    private final InstitutionPortalService institutionPortalService;

    public InstitutionPortalController(InstitutionPortalService institutionPortalService) {
        this.institutionPortalService = institutionPortalService;
    }

    @GetMapping("/me")
    public InstitutionPortalProfileDto me(@RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org) {
        return institutionPortalService.profile(org);
    }

    @GetMapping("/cats/priority")
    public List<CatPriorityDto> priority(@RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org) {
        return institutionPortalService.priorityCats(org);
    }

    @GetMapping(value = "/export/cats-health.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(@RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org) {
        String csv = institutionPortalService.exportCatsHealthCsv(org);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cats-health-" + org.getId() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    @GetMapping("/feeding-stations")
    public List<FeedingStationEntity> feedingStations(
            @RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org
    ) {
        return institutionPortalService.listFeedingStations(org);
    }

    @PatchMapping("/feeding-stations/{id}")
    public FeedingStationEntity patchStation(
            @RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org,
            @PathVariable("id") long id,
            @RequestBody PatchFeedingStationRequest body
    ) {
        return institutionPortalService.patchFeedingStation(org, id, body);
    }

    @GetMapping("/volunteers")
    public List<PartnerVolunteerDto> volunteers(
            @RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org
    ) {
        return institutionPortalService.listVolunteers(org);
    }

    @PostMapping("/volunteers")
    public PartnerVolunteerDto addVolunteer(
            @RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org,
            @Valid @RequestBody AddPartnerVolunteerRequest body
    ) {
        return institutionPortalService.addVolunteer(org, body.email(), body.displayName(), body.roleTag());
    }

    @DeleteMapping("/volunteers/{id}")
    public void deleteVolunteer(
            @RequestAttribute(InstitutionRequestAttributes.ORGANIZATION) OrganizationEntity org,
            @PathVariable("id") long id
    ) {
        institutionPortalService.deleteVolunteer(org, id);
    }
}
