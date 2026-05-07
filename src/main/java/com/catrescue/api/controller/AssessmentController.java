package com.catrescue.api.controller;

import com.catrescue.api.domain.Assessment;
import com.catrescue.api.dto.AssessmentListItemDto;
import com.catrescue.api.dto.AssessmentResponse;
import com.catrescue.api.dto.StoredAssessmentImage;
import com.catrescue.api.security.data.DataAccessResolver;
import com.catrescue.api.security.data.DataAccessTier;
import com.catrescue.api.security.data.DataMaskingService;
import com.catrescue.api.service.AssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    /**
     * Path segment must be a UUID so {@code /list}, {@code /image}, etc. are never captured as {@code {id}}.
     */
    private static final String ID_UUID = "{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    private final AssessmentService assessmentService;
    private final DataAccessResolver dataAccessResolver;
    private final DataMaskingService dataMaskingService;

    public AssessmentController(
            AssessmentService assessmentService,
            DataAccessResolver dataAccessResolver,
            DataMaskingService dataMaskingService
    ) {
        this.assessmentService = assessmentService;
        this.dataAccessResolver = dataAccessResolver;
        this.dataMaskingService = dataMaskingService;
    }

    /**
     * Lists all assessments (domain model), newest {@code createdAt} first.
     */
    @CrossOrigin(origins = "*")
    @GetMapping
    public ResponseEntity<List<Assessment>> getAllAssessments(HttpServletRequest request) {
        if (dataAccessResolver.resolve(request) != DataAccessTier.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(assessmentService.getAllAssessments());
    }

    /**
     * Recent community assessments (max 500). Path is {@code /list} so {@code GET /api/v1/assessments}
     * never conflicts with multipart {@code POST} on the same base path (some setups otherwise return 405).
     */
    @GetMapping(path = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AssessmentListItemDto> listAssessments(HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        return dataMaskingService.maskAssessmentList(assessmentService.listCommunityAssessments(), tier);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssessmentResponse create(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "reporterUserId", required = false) Long reporterUserId,
            @RequestParam(value = "addressText", required = false) String addressText
    ) {
        return assessmentService.createAssessment(image, latitude, longitude, reporterUserId, addressText);
    }

    @GetMapping("/" + ID_UUID)
    public AssessmentResponse get(@PathVariable UUID id) {
        return assessmentService.getAssessment(id);
    }

    /**
     * Returns the stored upload bytes when {@code imagePersisted} was true for this assessment.
     */
    @GetMapping("/" + ID_UUID + "/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID id) {
        StoredAssessmentImage img = assessmentService.getAssessmentImage(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not stored for this assessment"));
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (img.contentType() != null && !img.contentType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(img.contentType());
            } catch (Exception ignored) {
                // keep APPLICATION_OCTET_STREAM
            }
        }
        return ResponseEntity.ok().contentType(contentType).body(img.bytes());
    }
}
