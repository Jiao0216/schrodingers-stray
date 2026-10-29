package com.catrescue.api.service;

import com.catrescue.api.config.AssessmentPersistenceProperties;
import com.catrescue.api.domain.Assessment;
import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.BranchType;
import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.domain.CatFeatureSnapshot;
import com.catrescue.api.dto.AssessmentActionGuidanceDto;
import com.catrescue.api.dto.AssessmentDuplicateMatchDto;
import com.catrescue.api.dto.AssessmentListItemDto;
import com.catrescue.api.dto.AssessmentResponse;
import com.catrescue.api.dto.AssessmentTimelineItemDto;
import com.catrescue.api.dto.AssessmentTrackingMatchDto;
import com.catrescue.api.dto.BranchActionsDto;
import com.catrescue.api.dto.CatProfileAiGuidanceResponse;
import com.catrescue.api.dto.CatFeaturesDto;
import com.catrescue.api.dto.ModelLabelsDto;
import com.catrescue.api.dto.StoredAssessmentImage;
import com.catrescue.api.dto.TnrLocationDto;
import com.catrescue.api.persistence.AssessmentEntity;
import com.catrescue.api.persistence.AssessmentJpaRepository;
import com.catrescue.api.repository.AssessmentRepository;
import com.catrescue.api.service.client.ClipEmbeddingClient;
import com.catrescue.api.service.client.MultimodalClient;
import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.catrescue.api.tracking.dto.NewSightingCommand;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.service.CatFeatureExtractionService;
import com.catrescue.api.tracking.service.CatHeatmapService;
import com.catrescue.api.tracking.service.SightingDeduplicationService;
import com.catrescue.api.tracking.vector.StrayCatVectorMetadata;
import com.catrescue.api.tracking.vector.VectorClient;
import com.catrescue.api.util.CoordinatePrecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private static final String DISCLAIMER_ZH = "本应用不对动物做诊断。紧急情况请联系兽医或当地动物管理部门。";
    private static final String RESOURCES_UNAVAILABLE =
            "Nearby resource list temporarily unavailable; please search local shelters or TNR groups.";
    private static final String RESOURCES_UNAVAILABLE_ZH = "附近资源列表暂时无法获取，请自行搜索当地收容所或 TNR 组织。";

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
    private final SightingJpaRepository sightingJpaRepository;
    private final AssessmentJpaRepository assessmentJpaRepository;
    private final CatJpaRepository catJpaRepository;
    private final VectorClient vectorClient;
    private final ClipEmbeddingClient clipEmbeddingClient;
    private final AssessmentActionGuidanceService assessmentActionGuidanceService;

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
            AssessmentDedupService assessmentDedupService,
            SightingJpaRepository sightingJpaRepository,
            AssessmentJpaRepository assessmentJpaRepository,
            CatJpaRepository catJpaRepository,
            VectorClient vectorClient,
            ClipEmbeddingClient clipEmbeddingClient,
            AssessmentActionGuidanceService assessmentActionGuidanceService
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
        this.sightingJpaRepository = sightingJpaRepository;
        this.assessmentJpaRepository = assessmentJpaRepository;
        this.catJpaRepository = catJpaRepository;
        this.vectorClient = vectorClient;
        this.clipEmbeddingClient = clipEmbeddingClient;
        this.assessmentActionGuidanceService = assessmentActionGuidanceService;
    }

    public AssessmentResponse createAssessment(
            MultipartFile image,
            Double latitude,
            Double longitude,
            Long reporterUserId,
            String addressText,
            String earClip
    ) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (earClip == null || earClip.isBlank()) {
            throw new IllegalArgumentException("is_ear_tipped is required (earClip: yes or no)");
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
        String userEarClip = normalizeEarClip(earClip);

        byte[] headCrop = clipEmbeddingClient.cropHead(bytes, contentType).orElse(null);

        ModelLabels labels;
        try {
            labels = multimodalClient.analyzeImageBytes(bytes, contentType, headCrop);
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
                    id,
                    userEarClip,
                    null
            );
            Assessment saved = repository.save(failed, payload);
            return toResponse(saved, null, null, null);
        }
        BranchType branch = routingService.decide(labels);

        String mime = contentType != null && !contentType.isBlank() ? contentType : "image/jpeg";
        String dataUrlForFeatures = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

        CatFeatureSnapshot snapshot = null;
        try {
            CatFeatureVector extracted = catFeatureExtractionService.extractFromImage(dataUrlForFeatures, headCrop);
            snapshot = CatFeatureSnapshot.fromVector(extracted);
        } catch (Exception ex) {
            log.warn("Assessment feature extraction skipped: {}", ex.toString());
        }

        UUID duplicateOf = null;
        Double duplicateScore = null;
        UUID clusterId = id;
        Optional<AssessmentDedupService.ScoredAssessmentMatch> featureHint = Optional.empty();
        if (snapshot != null) {
            try {
                featureHint = assessmentDedupService.findBestMatchingPriorAssessment(snapshot, id);
            } catch (Exception ex) {
                log.warn("Assessment feature hint skipped: {}", ex.toString());
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
                clusterId,
                userEarClip,
                null
        );
        Assessment saved = repository.save(assessment, payload);

        AssessmentTrackingMatchDto trackingMatch = ingestTrackingSightingIfRequested(
                bytes,
                contentType,
                saved,
                reporterUserId,
                addressText
        );
        Assessment reloaded = reloadAssessment(saved.id());
        AssessmentDuplicateMatchDto hintDuplicate = featureHint
                .flatMap(h -> buildHintDuplicateMatch(h, reloaded))
                .orElse(null);
        AssessmentActionGuidanceDto guidance = assessmentActionGuidanceService.build(
                userEarClip,
                reloaded.latitude(),
                reloaded.longitude()
        );
        return toResponse(reloaded, trackingMatch, hintDuplicate, guidance);
    }

    private Assessment reloadAssessment(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Assessment not found"));
    }

    private void indexAssessmentInChroma(Assessment saved, byte[] bytes, String contentType, float[] knownEmbedding) {
        if (!vectorClient.isEnabled()) {
            return;
        }
        float[] embedding = knownEmbedding;
        if (embedding == null) {
            embedding = clipEmbeddingClient.embedBytes(bytes, saved.originalFilename(), contentType).orElse(null);
        }
        if (embedding == null) {
            return;
        }
        String chromaId = "assessment_" + saved.id();
        String loc = locationSummary(saved);
        StrayCatVectorMetadata metadata = new StrayCatVectorMetadata(
                saved.trackingSightingId() != null ? saved.trackingSightingId() : 0L,
                null,
                saved.createdAt().toString(),
                loc,
                saved.imageStored() ? "/api/v1/assessments/" + saved.id() + "/image" : ""
        );
        if (!vectorClient.add(chromaId, embedding, metadata)) {
            log.warn("Failed to index assessment {} in Chroma", saved.id());
        }
    }

    private static String normalizeEarClip(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("is_ear_tipped is required (earClip: yes or no)");
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "yes", "no" -> v;
            default -> throw new IllegalArgumentException("Invalid earClip: must be yes or no");
        };
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
        return toResponse(a, null, null, null);
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
            linkAssessmentToSighting(saved.id(), se.getId());
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

    private void linkAssessmentToSighting(UUID assessmentId, Long sightingId) {
        if (assessmentId == null || sightingId == null || sightingId <= 0) {
            return;
        }
        assessmentJpaRepository.findById(assessmentId).ifPresent(entity -> {
            entity.setTrackingSightingId(sightingId);
            assessmentJpaRepository.save(entity);
        });
    }

    public Optional<StoredAssessmentImage> getAssessmentImage(UUID id) {
        return repository.findStoredImage(id);
    }

    private AssessmentResponse toResponse(Assessment a, AssessmentTrackingMatchDto trackingMatch) {
        return toResponse(a, trackingMatch, null, null);
    }

    private AssessmentResponse toResponse(
            Assessment a,
            AssessmentTrackingMatchDto trackingMatch,
            AssessmentDuplicateMatchDto hintDuplicate,
            AssessmentActionGuidanceDto guidanceOverride
    ) {
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
                ml.healthSuspectedInjuryPercent() >= 0 ? ml.healthSuspectedInjuryPercent() : null,
                ml.rationalePhrasesEn() != null ? ml.rationalePhrasesEn() : List.of(),
                ml.earTipBasisEn() != null ? ml.earTipBasisEn() : ""
        );
        BranchActionsDto actions;
        try {
            actions = a.status() == AssessmentStatus.FAILED
                    ? failedActions(a.failureReason())
                    : buildActions(a.branchType(), a.latitude(), a.longitude(), a.modelLabels());
        } catch (Exception ex) {
            log.warn("buildActions failed, using minimal actions: {}", ex.toString());
            actions = resourcesUnavailableActions();
        }
        AssessmentDuplicateMatchDto duplicateDto = hintDuplicate;
        if (duplicateDto == null && a.catFeatures() != null) {
            try {
                duplicateDto = assessmentDedupService
                        .findBestMatchingPriorAssessment(a.catFeatures(), a.id())
                        .flatMap(h -> buildHintDuplicateMatch(h, a))
                        .orElse(null);
            } catch (Exception ex) {
                log.warn("buildHintDuplicateMatch skipped: {}", ex.toString());
            }
        }
        AssessmentActionGuidanceDto actionGuidance = guidanceOverride != null
                ? guidanceOverride
                : assessmentActionGuidanceService.build(a.userEarClip(), a.latitude(), a.longitude());
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
                duplicateDto,
                actionGuidance
        );
    }

    private Optional<AssessmentDuplicateMatchDto> buildHintDuplicateMatch(
            AssessmentDedupService.ScoredAssessmentMatch match,
            Assessment current
    ) {
        if (match == null || match.prior() == null || match.prior().getId() == null) {
            return Optional.empty();
        }
        Assessment prior = repository.findById(match.prior().getId()).orElse(null);
        if (prior == null) {
            return Optional.empty();
        }
        List<AssessmentTimelineItemDto> timeline = List.of(
                toTimelineItem(prior),
                toTimelineItem(current)
        ).stream().sorted(Comparator.comparing(AssessmentTimelineItemDto::createdAt)).toList();
        return Optional.of(new AssessmentDuplicateMatchDto(
                prior.id(),
                match.score(),
                prior.createdAt(),
                locationSummary(prior),
                timeline
        ));
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
        boolean sameCatBadge = a.duplicateOfAssessmentId() != null;
        double sick = a.modelLabels().sickOrInjuredConfidence();
        int inj = a.modelLabels().healthSuspectedInjuryPercent();
        int sev = a.modelLabels().healthSeverelyEmaciatedPercent();
        boolean needsHelp = a.branchType() == BranchType.RESCUE || sick >= 0.55
                || inj >= 70 || sev >= 70;
        boolean tnrVerified = "TNR_VERIFIED".equals(neuteredStatusLabel(a));
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
        String userClip = a.userEarClip() != null ? a.userEarClip().trim().toLowerCase(Locale.ROOT) : "unknown";
        if ("yes".equals(userClip)) {
            return "TNR_VERIFIED";
        }
        if ("no".equals(userClip)) {
            return "LIKELY_INTACT";
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

    private static BranchActionsDto resourcesUnavailableActions() {
        return new BranchActionsDto(
                DISCLAIMER,
                List.of(RESOURCES_UNAVAILABLE),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                DISCLAIMER_ZH,
                List.of(RESOURCES_UNAVAILABLE_ZH),
                null,
                null,
                Collections.emptyList()
        );
    }

    private BranchActionsDto failedActions(String failureReason) {
        // The raw reason is a technical (English) error; the zh field carries a readable message instead.
        String detail = failureReason != null ? failureReason : "Inference failed.";
        return new BranchActionsDto(
                DISCLAIMER,
                List.of(detail),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                DISCLAIMER_ZH,
                List.of("模型分析失败，请稍后重试或换一张更清晰的照片。"),
                null,
                null,
                Collections.emptyList()
        );
    }

    private static List<String> buildHealthGuidanceLines(ModelLabels m, boolean zh) {
        List<String> out = new ArrayList<>();
        if (m.healthSuspectedInjuryPercent() >= 70) {
            out.add(zh
                    ? "疑似受伤：志愿者应尽快跟进。可通过下方「附近救助」联系收容所或急诊。"
                    : "Suspected injury: volunteers should follow up urgently. Use «Nearby rescue» below to contact a shelter or emergency care.");
        }
        if (m.healthSeverelyEmaciatedPercent() >= 70) {
            out.add(zh
                    ? "明显严重消瘦：请联系附近的 TNR 或救助组织，协调诱捕并安排兽医评估。"
                    : "Severe wasting visible: contact a nearby TNR or rescue group for coordinated trapping and veterinary assessment.");
        } else if (m.healthUndernutritionPercent() >= 70) {
            out.add(zh
                    ? "可能营养不良：请密切观察、协调投喂，待猫状态稳定后再联系 TNR 组织。"
                    : "Possible undernutrition: monitor closely, coordinate feeding, and involve TNR groups when the cat is stable.");
        }
        return out;
    }

    private BranchActionsDto buildActions(BranchType branch, Double lat, Double lng, ModelLabels labels) {
        List<String> healthGuidance = buildHealthGuidanceLines(labels, false);
        List<String> healthGuidanceZh = buildHealthGuidanceLines(labels, true);
        List<String> rescueSteps = List.of(
                "If the animal is bleeding, non-responsive, or hit by a car: seek emergency veterinary care immediately.",
                "Contact City of San Jose Animal Care & Services or a local rescue for coordinated help.",
                "Avoid chasing; use a towel or carrier if safe to reduce stress."
        );
        List<String> rescueStepsZh = List.of(
                "如果动物在流血、没有反应或被车撞：请立即就医（兽医急诊）。",
                "联系圣何塞市动物关爱服务处（City of San Jose Animal Care & Services）或当地救助组织协同处理。",
                "不要追赶；在安全的前提下用毛巾或猫包减轻它的应激。"
        );
        List<String> generalSteps = List.of(
                "Observe from a distance; note location and time.",
                "Consider TNR programs if cats are recurring; coordinate with neighbors respectfully."
        );
        List<String> generalStepsZh = List.of(
                "远距离观察，记下地点和时间。",
                "如果经常出现，可考虑 TNR 项目；与邻居友善沟通协调。"
        );

        double refLat = lat != null ? lat : 37.3382;
        double refLng = lng != null ? lng : -121.8863;
        List<TnrLocationDto> rescueList = rescueDirectoryService.nearestDtos(refLat, refLng, 5);
        List<TnrLocationDto> tnrList = tnrDirectoryService.nearestDtos(refLat, refLng, 5);
        String nextdoorTitle = feedingCopyService.suggestedTitle();
        String nextdoorBody = feedingCopyService.buildPost("San Jose area", lat, lng);
        String nextdoorTitleZh = feedingCopyService.suggestedTitleZh();
        String nextdoorBodyZh = feedingCopyService.buildPostZh("圣何塞地区", lat, lng);

        List<String> steps = switch (branch) {
            case RESCUE -> rescueSteps;
            case GENERAL_GUIDANCE -> generalSteps;
            case FEEDING_OUTREACH, TNR_RESOURCES -> Collections.emptyList();
        };
        List<String> stepsZh = switch (branch) {
            case RESCUE -> rescueStepsZh;
            case GENERAL_GUIDANCE -> generalStepsZh;
            case FEEDING_OUTREACH, TNR_RESOURCES -> Collections.emptyList();
        };
        return new BranchActionsDto(
                DISCLAIMER,
                steps,
                nextdoorTitle,
                nextdoorBody,
                rescueList,
                tnrList,
                healthGuidance,
                DISCLAIMER_ZH,
                stepsZh,
                nextdoorTitleZh,
                nextdoorBodyZh,
                healthGuidanceZh
        );
    }

    /**
     * Reconstructs branch-specific handling guidance from the nearest completed photo assessment
     * (same 120m / 15min window as sighting image fallback).
     */
    public Optional<CatProfileAiGuidanceResponse> findAiGuidanceForCat(long catId) {
        Instant since = Instant.now().minus(60, ChronoUnit.DAYS);
        List<SightingEntity> recent = sightingJpaRepository.findByCatIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                catId, since);
        List<SightingEntity> toScan = new ArrayList<>(recent);
        if (toScan.isEmpty()) {
            sightingJpaRepository.findFirstByCatIdOrderByOccurredAtDesc(catId).ifPresent(toScan::add);
        }
        for (SightingEntity s : toScan.stream().limit(25).toList()) {
            Optional<CatProfileAiGuidanceResponse> out = findClosestCompletedAssessmentIdNear(s)
                    .flatMap(aid -> buildAiGuidance(aid, "NEAR_SIGHTING"));
            if (out.isPresent()) {
                return out;
            }
        }
        return catJpaRepository.findById(catId).flatMap(cat ->
                findClosestCompletedAssessmentIdNear(cat.getLastSeenLat(), cat.getLastSeenLng(), cat.getLastSeenAt())
                        .flatMap(aid -> buildAiGuidance(aid, "NEAR_PROFILE")));
    }

    private Optional<CatProfileAiGuidanceResponse> buildAiGuidance(UUID assessmentId, String matchKind) {
        return repository.findById(assessmentId).map(a -> {
            BranchActionsDto actions;
            try {
                actions = a.status() == AssessmentStatus.FAILED
                        ? failedActions(a.failureReason())
                        : buildActions(a.branchType(), a.latitude(), a.longitude(), a.modelLabels());
            } catch (Exception ex) {
                log.warn("findAiGuidance buildActions failed: {}", ex.toString());
                actions = resourcesUnavailableActions();
            }
            List<String> rationale = a.modelLabels().rationalePhrases() != null
                    ? List.copyOf(a.modelLabels().rationalePhrases())
                    : List.of();
            return new CatProfileAiGuidanceResponse(
                    assessmentId,
                    a.createdAt(),
                    a.branchType().name(),
                    matchKind,
                    actions.disclaimer(),
                    actions.rescueNextSteps() != null ? List.copyOf(actions.rescueNextSteps()) : List.of(),
                    actions.healthGuidanceLines() != null ? List.copyOf(actions.healthGuidanceLines()) : List.of(),
                    rationale,
                    actions.disclaimerZh(),
                    actions.rescueNextStepsZh() != null ? List.copyOf(actions.rescueNextStepsZh()) : List.of(),
                    actions.healthGuidanceLinesZh() != null ? List.copyOf(actions.healthGuidanceLinesZh()) : List.of(),
                    a.modelLabels().rationalePhrasesEn() != null ? List.copyOf(a.modelLabels().rationalePhrasesEn()) : List.of()
            );
        });
    }

    private Optional<UUID> findClosestCompletedAssessmentIdNear(SightingEntity s) {
        Instant at = s.getOccurredAt() != null ? s.getOccurredAt() : Instant.now();
        return findClosestCompletedAssessmentIdNear(s.getLatitude(), s.getLongitude(), at);
    }

    private Optional<UUID> findClosestCompletedAssessmentIdNear(double lat, double lng, Instant occurredAt) {
        if (lat == 0.0 && lng == 0.0) {
            return Optional.empty();
        }
        Instant ref = occurredAt != null ? occurredAt : Instant.now();
        List<AssessmentEntity> rows = assessmentJpaRepository.findTop500ByOrderByCreatedAtDesc();
        AssessmentEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (AssessmentEntity a : rows) {
            if (a.getStatus() != AssessmentStatus.COMPLETED) {
                continue;
            }
            if (a.getLatitude() == null || a.getLongitude() == null) {
                continue;
            }
            double meter = distanceMetersAssessment(lat, lng, a.getLatitude(), a.getLongitude());
            long dt = Math.abs(Duration.between(ref, a.getCreatedAt()).getSeconds());
            if (meter > 120.0 || dt > 900) {
                continue;
            }
            double score = meter + (dt / 3.0);
            if (score < bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.getId());
    }

    private static double distanceMetersAssessment(double lat1, double lng1, double lat2, double lng2) {
        final double rEarth = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double aa = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(Math.max(0.0, 1 - aa)));
        return rEarth * c;
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
