package com.catrescue.api.institution.web;

import com.catrescue.api.institution.dto.SubmitInstitutionApplicationRequest;
import com.catrescue.api.institution.persistence.InstitutionApplicationEntity;
import com.catrescue.api.institution.service.InstitutionApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/institution/applications")
public class InstitutionPublicApplicationController {

    private final InstitutionApplicationService institutionApplicationService;

    public InstitutionPublicApplicationController(InstitutionApplicationService institutionApplicationService) {
        this.institutionApplicationService = institutionApplicationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody SubmitInstitutionApplicationRequest body) {
        InstitutionApplicationEntity saved = institutionApplicationService.submit(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "status", saved.getStatus().name()
        ));
    }
}
