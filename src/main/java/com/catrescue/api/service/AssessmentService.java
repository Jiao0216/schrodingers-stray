package com.catrescue.api.service;

import com.catrescue.api.config.AssessmentPersistenceProperties;
import com.catrescue.api.domain.Assessment;
import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.BranchType;
import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.domain.CatFeatureSnapshot;
import com.catrescue.api.dto.AssessmentDuplicateMatchDto;
import com.catrescue.api.dto.AssessmentListItemDto;
import com.catrescue.api.dto.AssessmentResponse;
import com.catrescue.api.dto.AssessmentTimelineItemDto;
import com.catrescue.api.dto.AssessmentTrackingMatchDto;
import com.catrescue.api.dto.BranchActionsDto;
import com.catrescue.api.dto.CatFeaturesDto;
import com.catrescue.api.dto.ModelLabelsDto;
import com.catrescue.api.dto.StoredAssessmentImage;
import com.catrescue.api.dto.TnrLocationDto;
import com.catrescue.api.repository.AssessmentRepository;
import com.catrescue.api.service.client.MultimodalClient;
import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.catrescue.api.tracking.dto.NewSightingCommand;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.service.CatFeatureExtractionService;
import com.catrescue.api.tracking.service.CatHeatmapService;
import com.catrescue.api.tracking.service.SightingDeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private static final String DISCLAIMER =
            "This app does not diagnose animals. For emergencies, contact a veterinarian or local animal control.";

    private final AssessmentRepository repository;
    private final MultimodalClient multimodalClient;
    private final RoutingService routingService;
    private final FeedingCopyService feedingCopyService;
    private final TnrDirectoryService tnrDirectoryService;
    private final RescueDirectoryService rescueDirectoryService;
    private final AssessmentPersistenceProperties assessmentPersistenceProperties;
    private final SightingDeduplicationService sightingDeduplicationService;
    private final CatHeatmapService catHeatmapService;
    private final CatFeatureExtractionService catFeatureExtractionService;
    private final AssessmentDedupService assessmentDedupService;

    public AssessmentService(
            AssessmentRepository repository,
            MultimodalClient multimodalClient,
            RoutingService routingService,
            FeedingCopyService feedingCopyService,
            TnrDirectoryService tnrDirectoryService,
            RescueDirectoryService rescueDirectoryService,
            AssessmentPersistenceProperties assessmentPersistenceProperties,
            SightingDeduplicationService sightingDeduplicationService,
            CatHeatmapService catHeatmapService,
            CatFeatureExtractionService catFeatureExtractionService,
            AssessmentDedupService assessmentDedupService
    ) {
        this.repository = repository;
        this.multimodalClient = multimodalClient;
        this.routingService = routingService;
        this.feedingCopyService = feedingCopyService;
        this.tnrDirectoryService = tnrDirectoryService;
        this.rescueDirectoryService = rescueDirectoryService;
        this.assessmentPersistenceProperties = assessmentPersistenceProperties;
        this.sightingDeduplicationService = sightingDeduplicationService;
        this.catHeatmapService = catHeatmapService;
        this.catFeatureExtractionService = catFeatureExtractionService;
        this.assessmentDedupService = assessmentDedupService;
    }

    public AssessmentResponse createAssessment(
            MultipartFile image,
            Double latitude,
            Double longitude,
            Long reporterUserId,
            String addressText
    ) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded image", e);
        }
        String originalFilename = sanitizeFilename(image.getOriginalFilename());
        String contentType = image.getContentType();
        byte[] payload = storePayload(bytes);
        String addr = normalizeAddress(addressText);

        ModelLabels labels;
        try {
            labels = multimodalClient.analyzeImageBytes(bytes, contentType);
        } catch (Exception e) {
            log.warn("Multimodal inference failed: {}", e.toString());
            Assessment failed = new Assessment(
                    id,
                    AssessmentStatus.FAILED,
                    originalFilename,
                    contentType,
                    latitude,
                    longitude,
                    BranchType.GENERAL_GUIDANCE,
                    new ModelLabels(0, 0, 0, List.of("Inference failed"), false, false, "", "medium", false, false, -1, "", -1, -1, -1, -1),
                    e.getMessage(),
                    now,
                    now,
                    false,
                    addr,
                    null,
                    null,
                    null,
                    id
            );
            Assessment saved = repository.save(failed, payload);
            return toResponse(saved, null);
        }
        BranchType branch = routingService.decide(labels);

        String mime = contentType != null && !contentType.isBlank() ? contentType : "image/jpeg";
        String dataUrlForFeatures = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

        CatFeatureSnapshot snapshot = null;
        try {
            CatFeatureVector extracted = catFeatureExtractionService.extractFromImage(dataUrlForFeatures);
            snapshot = CatFeatureSnapshot.fromVector(extracted);
        } catch (Exception ex) {
            log.warn("Assessment feature extraction skipped: {}", ex.toString());
        }

        UUID duplicateOf = null;
        Double duplicateScore = null;
        UUID clusterId = id;
        if (snapshot != null) {
            try {
                Optional<AssessmentDedupService.ScoredAssessmentMatch> dup =
                        assessmentDedupService.findBestMatchingPriorAssessment(snapshot, id);
                if (dup.isPresent()) {
                    var priorEntity = dup.get().prior();
                    duplicateOf = priorEntity.getId();
                    duplicateScore = dup.get().score();
                    clusterId = priorEntity.getIdentityClusterId() != null
                            ? priorEntity.getIdentityClusterId()
                            : priorEntity.getId();
                }
            } catch (Exception ex) {
                log.warn("Assessment dedup skipped (assessment still saved): {}", ex.toString());
            }
        }

        Assessment assessment = new Assessment(
                id,
                AssessmentStatus.COMPLETED,
                originalFilename,
                contentType,
                latitude,
                longitude,
                branch,
                labels,
                null,
                now,
                now,
                false,
                addr,
                snapshot,
                duplicateOf,
                duplicateScore,
                clusterId
        );
        Assessment saved = repository.save(assessment, payload);

        AssessmentTrackingMatchDto trackingMatch = ingestTrackingSightingIfRequested(
                bytes,
                contentType,
                saved,
                reporterUserId,
                addressText
        );
        return toResponse(saved, trackingMatch);
    }

    public List<AssessmentListItemDto> listCommunityAssessments() {
        return repository.findTop500ByCreatedAtDesc().stream()
                .map(this::toListItem)
                .toList();
    }

    /** All persisted assessments, newest first (for {@code GET /api/v1/assessments}). */
    public List<Assessment> getAllAssessments() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String normalizeAddress(String addressText) {
        if (addressText == null) {
            return null;
        }
        String t = addressText.trim();
        return t.isEmpty() ? null : t;
    }

    private byte[] storePayload(byte[] rawBytes) {
        return assessmentPersistenceProperties.isPersistImages() ? rawBytes : null;
    }

    public AssessmentResponse getAssessment(UUID id) {
        Assessment a = repository.findById(id).orElseThrow(() -> new NotFoundException("Assessment not found"));
        return toResponse(a, null);
    }

    private AssessmentTrackingMatchDto ingestTrackingSightingIfRequested(
            byte[] imageBytes,
            String contentType,
            Assessment saved,
            Long reporterUserId,
            String addressText
    ) {
        if (reporterUserId == null || reporterUserId <= 0) {
            return null;
        }
        if (saved.latitude() == null || saved.longitude() == null) {
            return null;
        }
        try {
            String mime = contentType != null && !contentType.isBlank() ? contentType : "image/jpeg";
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
            SightingEntity se = sightingDeduplicationService.reportSighting(new NewSightingCommand(
                    reporterUserId,
                    dataUrl,
                    addressText,
                    saved.latitude(),
                    saved.longitude(),
                    null
            ));
            Long exclude = se.getCatId() != null ? se.getCatId() : se.getSuggestedCatId();
            int nearby = catHeatmapService.countDistinctOtherCatsNear(
                    saved.latitude(),
                    saved.longitude(),
                    exclude,
                    1500,
                    CatHeatmapService.HEATMAP_WINDOW_DAYS
            );
            return new AssessmentTrackingMatchDto(
                    se.getId(),
                    se.getCatId(),
                    se.getDedupStatus() != null ? se.getDedupStatus().name() : null,
                    se.getSuggestedCatId(),
                    se.getDuplicateOfSightingId(),
                    se.getSimilarityScore(),
                    se.getDedupReason(),
                    nearby
            );
        } catch (Exception ex) {
            log.warn("Assessment sighting sidecar failed (assessment still saved): {}", ex.toString());
            return null;
        }
    }

    public Optional<StoredAssessmentImage> getAssessmentImage(UUID id) {
        return repository.findStoredImage(id);
    }

    private AssessmentResponse toResponse(Assessment a, AssessmentTrackingMatchDto trackingMatch) {
        boolean likelyAlreadySterilized = isLikelyAlreadySterilized(a.modelLabels());
        String captureAdvice = likelyAlreadySterilized
                ? "Ear-tip detected: treat as already sterilized. Avoid repeat trapping unless medical care is needed."
                : "No ear-tip detected: treat as needing sterilization (TNR) and coordinate trapping safely.";
        Integer earTipPct = a.modelLabels().earTipConfidencePercent() >= 0
                ? a.modelLabels().earTipConfidencePercent()
                : null;
        String earTipBasis = a.modelLabels().earTipBasis() != null ? a.modelLabels().earTipBasis() : "";
        ModelLabels ml = a.modelLabels();
        ModelLabelsDto labelsDto = new ModelLabelsDto(
                ml.sickOrInjuredConfidence(),
                ml.needsFeedingConfidence(),
                ml.likelyNotNeuteredOrEarNotTippedConfidence(),
                ml.rationalePhrases(),
                likelyAlreadySterilized,
                captureAdvice,
                ml.earTipDetected(),
                ml.earTipConfidenceLevel() != null ? ml.earTipConfidenceLevel() : "",
                ml.imageQualityLevel() != null ? ml.imageQualityLevel() : "medium",
                ml.acuteSymptomsVisible(),
                earTipPct,
                earTipBasis,
                ml.healthBodyNormalPercent() >= 0 ? ml.healthBodyNormalPercent() : null,
                ml.healthUndernutritionPercent() >= 0 ? ml.healthUndernutritionPercent() : null,
                ml.healthSeverelyEmaciatedPercent() >= 0 ? ml.healthSeverelyEmaciatedPercent() : null,
                ml.healthSuspectedInjuryPercent() >= 0 ? ml.healthSuspectedInjuryPercent() : null
        );
        BranchActionsDto actions;
        try {
            actions = a.status() == AssessmentStatus.FAILED
                    ? failedActions(a.failureReason())
                    : buildActions(a.branchType(), a.latitude(), a.longitude(), a.modelLabels());
        } catch (Exception ex) {
            log.warn("buildActions failed, using minimal actions: {}", ex.toString());
            actions = new BranchActionsDto(
                    DISCLAIMER,
                    List.of("Nearby resource list temporarily unavailable; please search local shelters or TNR groups."),
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        AssessmentDuplicateMatchDto duplicateDto = null;
        try {
            duplicateDto = buildDuplicateMatch(a);
        } catch (Exception ex) {
            log.warn("buildDuplicateMatch skipped: {}", ex.toString());
        }
        return new AssessmentResponse(
                a.id(),
                a.status(),
                a.branchType(),
                labelsDto,
                a.originalFilename(),
                a.contentType(),
                a.imageStored(),
                a.latitude(),
                a.longitude(),
                a.addressText(),
                actions,
                a.failureReason(),
                trackingMatch,
                duplicateDto
        );
    }

    private AssessmentDuplicateMatchDto buildDuplicateMatch(Assessment a) {
        if (a.duplicateOfAssessmentId() == null) {
            return null;
        }
        Assessment prior = repository.findById(a.duplicateOfAssessmentId()).orElse(null);
        if (prior == null) {
            return null;
        }
        UUID clusterKey = a.identityClusterId() != null ? a.identityClusterId() : a.id();
        List<Assessment> chain = repository.findByIdentityClusterIdOrdered(clusterKey);
        if (chain.isEmpty()) {
            chain = List.of(prior, a).stream().sorted(Comparator.comparing(Assessment::createdAt)).toList();
        }
        if (chain.size() > 60) {
            chain = chain.subList(0, 60);
        }
        List<AssessmentTimelineItemDto> timeline = chain.stream().map(this::toTimelineItem).toList();
        double score = a.duplicateSimilarityScore() != null ? a.duplicateSimilarityScore() : 0.0;
        return new AssessmentDuplicateMatchDto(
                prior.id(),
                score,
                prior.createdAt(),
                locationSummary(prior),
                timeline
        );
    }

    private AssessmentTimelineItemDto toTimelineItem(Assessment x) {
        String thumb = x.imageStored() ? "/api/v1/assessments/" + x.id() + "/image" : null;
        return new AssessmentTimelineItemDto(
                x.id(),
                x.createdAt(),
                locationSummary(x),
                healthStatusLabel(x),
                neuteredStatusLabel(x),
                thumb
        );
    }

    private AssessmentListItemDto toListItem(Assessment a) {
        UUID clusterKey = a.identityClusterId() != null ? a.identityClusterId() : a.id();
        long clusterSize = repository.countByIdentityClusterId(clusterKey);
        if (clusterSize <= 0) {
            clusterSize = 1;
        }
        boolean sameCatBadge = a.duplicateOfAssessmentId() != null || clusterSize > 1;
        double sick = a.modelLabels().sickOrInjuredConfidence();
        int inj = a.modelLabels().healthSuspectedInjuryPercent();
        int sev = a.modelLabels().healthSeverelyEmaciatedPercent();
        boolean needsHelp = a.branchType() == BranchType.RESCUE || sick >= 0.55
                || inj >= 70 || sev >= 70;
        boolean tnrVerified = isLikelyAlreadySterilized(a.modelLabels());
        boolean healthy = !needsHelp && sick < 0.45;
        String img = a.imageStored() ? "/api/v1/assessments/" + a.id() + "/image" : null;
        return new AssessmentListItemDto(
                a.id(),
                img,
                a.latitude(),
                a.longitude(),
                a.addressText(),
                healthStatusLabel(a),
                neuteredStatusLabel(a),
                a.createdAt(),
                catFeaturesDto(a.catFeatures()),
                a.imageStored(),
                a.identityClusterId(),
                (int) Math.min(Integer.MAX_VALUE, clusterSize),
                a.duplicateOfAssessmentId(),
                sameCatBadge,
                needsHelp,
                healthy,
                tnrVerified
        );
    }

    private static CatFeaturesDto catFeaturesDto(CatFeatureSnapshot s) {
        if (s == null) {
            return null;
        }
        return new CatFeaturesDto(
                s.coatColor(),
                s.patternType(),
                s.bodySize(),
                s.specialFeatures() != null ? s.specialFeatures() : List.of(),
                s.earTipped()
        );
    }

    private static String healthStatusLabel(Assessment a) {
        if (a.status() != AssessmentStatus.COMPLETED) {
            return "UNKNOWN";
        }
        ModelLabels m = a.modelLabels();
        double sick = m.sickOrInjuredConfidence();
        if (a.branchType() == BranchType.RESCUE || sick >= 0.55
                || m.healthSuspectedInjuryPercent() >= 70 || m.healthSeverelyEmaciatedPercent() >= 70) {
            return "NEEDS_HELP";
        }
        if (sick >= 0.35 || m.healthUndernutritionPercent() >= 70) {
            return "MONITOR";
        }
        return "HEALTHY";
    }

    private static String neuteredStatusLabel(Assessment a) {
        if (a.status() != AssessmentStatus.COMPLETED) {
            return "UNKNOWN";
        }
        if (isLikelyAlreadySterilized(a.modelLabels())) {
            return "TNR_VERIFIED";
        }
        return "LIKELY_INTACT";
    }

    private static String locationSummary(Assessment a) {
        if (a.addressText() != null && !a.addressText().isBlank()) {
            return a.addressText().trim();
        }
        if (a.latitude() != null && a.longitude() != null) {
            return String.format(Locale.ROOT, "%.5f, %.5f", a.latitude(), a.longitude());
        }
        return "Unknown";
    }

    private BranchActionsDto failedActions(String failureReason) {
        String detail = failureReason != null ? failureReason : "Inference failed.";
        return new BranchActionsDto(
                DISCLAIMER,
                List.of(detail),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private static List<String> buildHealthGuidanceLines(ModelLabels m) {
        List<String> out = new ArrayList<>();
        if (m.healthSuspectedInjuryPercent() >= 70) {
            out.add("Suspected injury: volunteers should follow up urgently. Use «Nearby rescue» below to contact a shelter or emergency care.");
        }
        if (m.healthSeverelyEmaciatedPercent() >= 70) {
            out.add("Severe wasting visible: contact a nearby TNR or rescue group for coordinated trapping and veterinary assessment.");
        } else if (m.healthUndernutritionPercent() >= 70) {
            out.add("Possible undernutrition: monitor closely, coordinate feeding, and involve TNR groups when the cat is stable.");
        }
        return out;
    }

    private BranchActionsDto buildActions(BranchType branch, Double lat, Double lng, ModelLabels labels) {
        List<String> healthGuidance = buildHealthGuidanceLines(labels);
        List<String> rescueSteps = List.of(
                "If the animal is bleeding, non-responsive, or hit by a car: seek emergency veterinary care immediately.",
                "Contact City of San Jose Animal Care & Services or a local rescue for coordinated help.",
                "Avoid chasing; use a towel or carrier if safe to reduce stress."
        );

        double refLat = lat != null ? lat : 37.3382;
        double refLng = lng != null ? lng : -121.8863;
        List<TnrLocationDto> rescueList = rescueDirectoryService.nearestDtos(refLat, refLng, 5);
        List<TnrLocationDto> tnrList = tnrDirectoryService.nearestDtos(refLat, refLng, 5);
        String nextdoorTitle = feedingCopyService.suggestedTitle();
        String nextdoorBody = feedingCopyService.buildPost("San Jose area", lat, lng);

        return switch (branch) {
            case RESCUE -> new BranchActionsDto(
                    DISCLAIMER,
                    rescueSteps,
                    nextdoorTitle,
                    nextdoorBody,
                    rescueList,
                    tnrList,
                    healthGuidance
            );
            case FEEDING_OUTREACH -> new BranchActionsDto(
                    DISCLAIMER,
                    Collections.emptyList(),
                    nextdoorTitle,
                    nextdoorBody,
                    rescueList,
                    tnrList,
                    healthGuidance
            );
            case TNR_RESOURCES -> new BranchActionsDto(
                    DISCLAIMER,
                    Collections.emptyList(),
                    nextdoorTitle,
                    nextdoorBody,
                    rescueList,
                    tnrList,
                    healthGuidance
            );
            case GENERAL_GUIDANCE -> new BranchActionsDto(
                    DISCLAIMER,
                    List.of(
                            "Observe from a distance; note location and time.",
                            "Consider TNR programs if cats are recurring; coordinate with neighbors respectfully."
                    ),
                    nextdoorTitle,
                    nextdoorBody,
                    rescueList,
                    tnrList,
                    healthGuidance
            );
        };
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "upload";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean isLikelyAlreadySterilized(ModelLabels labels) {
        // Strict rule: only visible ear-tip counts as already sterilized.
        return labels.likelyEarTipped();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
