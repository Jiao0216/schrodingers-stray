package com.catrescue.api.tracking.service;

import com.catrescue.api.config.SiteProperties;
import com.catrescue.api.dto.CatProfileAiGuidanceResponse;
import com.catrescue.api.service.AssessmentService;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.domain.VolunteerBadgeCode;
import com.catrescue.api.tracking.dto.CatProfileResponse;
import com.catrescue.api.tracking.dto.CatShareStoryDto;
import com.catrescue.api.tracking.dto.FeedingCheckInRequest;
import com.catrescue.api.tracking.dto.FeedingCheckInResponse;
import com.catrescue.api.tracking.dto.HeatmapSightingResponse;
import com.catrescue.api.tracking.dto.NearbyHelpCatDto;
import com.catrescue.api.tracking.dto.PatchVolunteerProfileRequest;
import com.catrescue.api.tracking.dto.RegisterVolunteerRequest;
import com.catrescue.api.tracking.dto.VolunteerBadgeDto;
import com.catrescue.api.tracking.dto.VolunteerCatHubSnapshot;
import com.catrescue.api.tracking.dto.VolunteerCreatedCatDto;
import com.catrescue.api.tracking.dto.VolunteerLeaderboardEntryDto;
import com.catrescue.api.tracking.dto.VolunteerLatestCatResponse;
import com.catrescue.api.tracking.dto.VolunteerProfileResponse;
import com.catrescue.api.tracking.dto.VolunteerRescueRecordDto;
import com.catrescue.api.tracking.dto.VolunteerSavedCatEntryDto;
import com.catrescue.api.tracking.dto.VolunteerStatsResponse;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.persistence.VolunteerBadgeEarnedEntity;
import com.catrescue.api.tracking.persistence.VolunteerFeedingCheckInEntity;
import com.catrescue.api.tracking.persistence.VolunteerSavedCatEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.FeedingStationJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.repository.UserJpaRepository;
import com.catrescue.api.tracking.repository.VolunteerBadgeEarnedJpaRepository;
import com.catrescue.api.tracking.repository.VolunteerFeedingCheckInJpaRepository;
import com.catrescue.api.tracking.repository.VolunteerSavedCatJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class VolunteerHubService {

    public static final int FEEDING_CHECKIN_POINTS = 10;

    /** Discovery radius for “nearby cats needing help” list (meters). */
    public static final double NEARBY_HELP_RADIUS_METERS = 1500.0;

    private final UserJpaRepository userJpaRepository;
    private final SightingJpaRepository sightingJpaRepository;
    private final CatJpaRepository catJpaRepository;
    private final FeedingStationJpaRepository feedingStationJpaRepository;
    private final VolunteerFeedingCheckInJpaRepository volunteerFeedingCheckInJpaRepository;
    private final VolunteerBadgeEarnedJpaRepository volunteerBadgeEarnedJpaRepository;
    private final SiteProperties siteProperties;
    private final CatHeatmapService catHeatmapService;
    private final AssessmentService assessmentService;
    private final VolunteerSavedCatJpaRepository volunteerSavedCatJpaRepository;
    private final ObjectMapper objectMapper;

    public VolunteerHubService(
            UserJpaRepository userJpaRepository,
            SightingJpaRepository sightingJpaRepository,
            CatJpaRepository catJpaRepository,
            FeedingStationJpaRepository feedingStationJpaRepository,
            VolunteerFeedingCheckInJpaRepository volunteerFeedingCheckInJpaRepository,
            VolunteerBadgeEarnedJpaRepository volunteerBadgeEarnedJpaRepository,
            SiteProperties siteProperties,
            CatHeatmapService catHeatmapService,
            AssessmentService assessmentService,
            VolunteerSavedCatJpaRepository volunteerSavedCatJpaRepository,
            ObjectMapper objectMapper
    ) {
        this.userJpaRepository = userJpaRepository;
        this.sightingJpaRepository = sightingJpaRepository;
        this.catJpaRepository = catJpaRepository;
        this.feedingStationJpaRepository = feedingStationJpaRepository;
        this.volunteerFeedingCheckInJpaRepository = volunteerFeedingCheckInJpaRepository;
        this.volunteerBadgeEarnedJpaRepository = volunteerBadgeEarnedJpaRepository;
        this.siteProperties = siteProperties;
        this.catHeatmapService = catHeatmapService;
        this.assessmentService = assessmentService;
        this.volunteerSavedCatJpaRepository = volunteerSavedCatJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserEntity register(RegisterVolunteerRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userJpaRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setDisplayName(req.displayName().trim());
        u.setPhone(req.phone() != null && !req.phone().isBlank() ? req.phone().trim() : null);
        u.setBio(req.bio() != null ? req.bio().trim() : null);
        u.setServiceLatitude(req.serviceLatitude());
        u.setServiceLongitude(req.serviceLongitude());
        u.setRole(UserRole.VOLUNTEER);
        u.setVolunteerPoints(0);
        u.setNotifyNearbyEnabled(true);
        return userJpaRepository.save(u);
    }

    public VolunteerProfileResponse getProfile(long userId) {
        UserEntity u = loadVolunteer(userId);
        syncBadges(u.getId());
        return toProfile(u, true);
    }

    @Transactional
    public VolunteerProfileResponse patchProfile(long userId, PatchVolunteerProfileRequest req) {
        UserEntity u = loadVolunteer(userId);
        if (req.displayName() != null && !req.displayName().isBlank()) {
            u.setDisplayName(req.displayName().trim());
        }
        if (req.phone() != null) {
            u.setPhone(req.phone().isBlank() ? null : req.phone().trim());
        }
        if (req.bio() != null) {
            u.setBio(req.bio().isBlank() ? null : req.bio().trim());
        }
        if (req.serviceLatitude() != null) {
            u.setServiceLatitude(req.serviceLatitude());
        }
        if (req.serviceLongitude() != null) {
            u.setServiceLongitude(req.serviceLongitude());
        }
        if (req.notifyNearbyEnabled() != null) {
            u.setNotifyNearbyEnabled(req.notifyNearbyEnabled());
        }
        userJpaRepository.save(u);
        syncBadges(u.getId());
        return toProfile(u, true);
    }

    public List<VolunteerRescueRecordDto> listRescueRecords(long userId, int page, int size) {
        loadVolunteer(userId);
        int p = Math.max(0, page);
        int sz = Math.min(50, Math.max(1, size));
        return sightingJpaRepository.findByReporterUserIdOrderByOccurredAtDesc(userId, PageRequest.of(p, sz)).getContent().stream()
                .map(VolunteerHubService::toRescueRecord)
                .toList();
    }

    /**
     * Cats whose {@code cats.created_by_user_id} matches this volunteer (e.g. first sighting created the archive),
     * including cases where no sighting row is returned for other reasons.
     */
    public List<VolunteerCreatedCatDto> listCatsCreatedByVolunteer(long userId) {
        loadVolunteer(userId);
        return catJpaRepository.findByCreatedByUserIdOrderByLastSeenAtDesc(userId, PageRequest.of(0, 50)).stream()
                .map(c -> new VolunteerCreatedCatDto(c.getId(), c.getLastSeenAt()))
                .toList();
    }

    public Optional<VolunteerLatestCatResponse> latestUploadedCat(long userId) {
        loadVolunteer(userId);
        List<SightingEntity> rows = sightingJpaRepository
                .findByReporterUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, 1))
                .getContent();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SightingEntity s = rows.get(0);
        Long catId = s.getCatId();
        CatEntity cat = catId != null ? catJpaRepository.findById(catId).orElse(null) : null;
        String catName = cat != null && cat.getDisplayName() != null && !cat.getDisplayName().isBlank()
                ? cat.getDisplayName()
                : (s.getCatId() != null ? "Cat #" + s.getCatId() : "My uploaded cat");
        String steril = cat != null && cat.getSterilizationStatus() != null
                ? cat.getSterilizationStatus().name()
                : SterilizationStatus.UNKNOWN.name();
        boolean ear = cat != null && cat.isEarTipped();
        return Optional.of(new VolunteerLatestCatResponse(
                s.getCatId(),
                catName,
                toPublicImageUrl(s.getImageUrl()),
                s.getOccurredAt(),
                s.getAddressText(),
                steril,
                ear
        ));
    }

    public VolunteerStatsResponse getStatsOnly(long userId) {
        loadVolunteer(userId);
        long sightings = sightingJpaRepository.countByReporterUserId(userId);
        long checkins = volunteerFeedingCheckInJpaRepository.countByUserId(userId);
        long badges = volunteerBadgeEarnedJpaRepository.findByUserIdOrderByEarnedAtAsc(userId).size();
        return new VolunteerStatsResponse(sightings, checkins, badges);
    }

    public List<NearbyHelpCatDto> nearbyCatsNeedingHelp(long userId) {
        UserEntity u = loadVolunteer(userId);
        if (u.getServiceLatitude() == null || u.getServiceLongitude() == null) {
            return List.of();
        }
        double lat = u.getServiceLatitude();
        double lng = u.getServiceLongitude();
        double r = NEARBY_HELP_RADIUS_METERS;
        double latDelta = r / 111_320.0;
        double lngDelta = r / (111_320.0 * Math.max(0.25, Math.abs(Math.cos(Math.toRadians(lat)))));
        List<CatEntity> rough = catJpaRepository.findLastSeenInTerritory(
                lat - latDelta,
                lat + latDelta,
                lng - lngDelta,
                lng + lngDelta
        );
        List<NearbyHelpCatDto> out = new ArrayList<>();
        for (CatEntity c : rough) {
            if (isEffectivelySterilized(c)) {
                continue;
            }
            double dist = distanceMeters(lat, lng, c.getLastSeenLat(), c.getLastSeenLng());
            if (dist > r) {
                continue;
            }
            out.add(new NearbyHelpCatDto(
                    c.getId(),
                    c.getDisplayName(),
                    Double.valueOf(c.getLastSeenLat()),
                    Double.valueOf(c.getLastSeenLng()),
                    Math.round(dist * 10.0) / 10.0,
                    priorityScore(c),
                    c.getSterilizationStatus().name(),
                    c.isEarTipped()
            ));
        }
        out.sort(Comparator.comparingDouble(NearbyHelpCatDto::priorityScore).reversed());
        return out;
    }

    @Transactional
    public FeedingCheckInResponse feedingCheckIn(long userId, FeedingCheckInRequest req) {
        UserEntity u = loadVolunteer(userId);
        Long stationId = req.feedingStationId();
        if (stationId != null) {
            feedingStationJpaRepository.findById(stationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "feeding station not found"));
        }
        VolunteerFeedingCheckInEntity e = new VolunteerFeedingCheckInEntity();
        e.setUserId(userId);
        e.setLatitude(req.latitude());
        e.setLongitude(req.longitude());
        e.setFeedingStationId(req.feedingStationId());
        e.setNote(req.note() != null && req.note().length() > 500 ? req.note().substring(0, 500) : req.note());
        e.setPointsAwarded(FEEDING_CHECKIN_POINTS);
        e.setCreatedAt(Instant.now());
        VolunteerFeedingCheckInEntity saved = volunteerFeedingCheckInJpaRepository.save(e);

        u.setVolunteerPoints(u.getVolunteerPoints() + FEEDING_CHECKIN_POINTS);
        userJpaRepository.save(u);

        List<VolunteerBadgeCode> newly = syncBadgesReturningNew(userId);
        return new FeedingCheckInResponse(
                saved.getId(),
                FEEDING_CHECKIN_POINTS,
                u.getVolunteerPoints(),
                saved.getCreatedAt(),
                newly
        );
    }

    public List<VolunteerBadgeDto> listBadges(long userId) {
        loadVolunteer(userId);
        syncBadges(userId);
        return volunteerBadgeEarnedJpaRepository.findByUserIdOrderByEarnedAtAsc(userId).stream()
                .map(b -> new VolunteerBadgeDto(b.getBadgeCode(), b.getEarnedAt()))
                .toList();
    }

    public List<VolunteerLeaderboardEntryDto> leaderboard(int limit) {
        int lim = Math.min(100, Math.max(1, limit));
        List<UserEntity> rows = userJpaRepository.findVolunteerLeaderboard(UserRole.VOLUNTEER, PageRequest.of(0, lim));
        List<VolunteerLeaderboardEntryDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            UserEntity u = rows.get(i);
            out.add(new VolunteerLeaderboardEntryDto(i + 1, u.getId(), sanitizeLeaderboardDisplayName(u.getDisplayName()), u.getVolunteerPoints()));
        }
        return out;
    }

    private static String sanitizeLeaderboardDisplayName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            return "匿名志愿者";
        }
        return name.toLowerCase(Locale.ROOT).contains("tracking user") ? "匿名志愿者" : name;
    }

    public CatShareStoryDto buildCatShareStory(long catId, Long volunteerUserId) {
        CatEntity cat = catJpaRepository.findById(catId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cat not found"));
        if (volunteerUserId != null) {
            loadVolunteer(volunteerUserId);
        }
        String name = cat.getDisplayName();
        if (name == null || name.isBlank()) {
            name = "猫咪 #" + cat.getId();
        }
        String base = siteProperties.getPublicBaseUrl() == null ? "" : siteProperties.getPublicBaseUrl().trim();
        String path = "/index.html?shareCatId=" + cat.getId();
        String url = base.isEmpty() ? path : (base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path);

        String zh = String.format(
                "我在「薛定谔的流浪猫」追踪到 %s，一起来关注流浪动物救助与 TNR。\n%s",
                name,
                url
        );
        String en = String.format(
                "Tracking stray cat \"%s\" on Schrödinger's Stray Cat — join TNR & rescue.\n%s",
                name,
                url
        );
        List<String> tags = List.of("流浪猫救助", "TNR", "薛定谔的流浪猫", "StrayCat");
        return new CatShareStoryDto(
                name + " · 流浪猫档案",
                "记录位置与健康线索，支持社区协作救助。",
                url,
                tags,
                zh,
                en
        );
    }

    @Transactional
    public VolunteerSavedCatEntryDto saveCatSnapshot(long userId, long catId) {
        loadVolunteer(userId);
        CatProfileResponse profile = catHeatmapService.buildProfile(catId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cat not found"));
        List<HeatmapSightingResponse> sightings = new ArrayList<>(catHeatmapService.buildHeatmapSightings(catId));
        if (sightings.size() > 40) {
            sightings = new ArrayList<>(sightings.subList(0, 40));
        }
        CatProfileAiGuidanceResponse ai = assessmentService.findAiGuidanceForCat(catId).orElse(null);
        VolunteerCatHubSnapshot snap = new VolunteerCatHubSnapshot(Instant.now(), profile, sightings, ai);
        String json;
        try {
            json = objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "snapshot serialization failed");
        }
        Optional<VolunteerSavedCatEntity> existing = volunteerSavedCatJpaRepository.findByUserIdAndCatId(userId, catId);
        VolunteerSavedCatEntity row = existing.orElseGet(VolunteerSavedCatEntity::new);
        row.setUserId(userId);
        row.setCatId(catId);
        row.setSnapshotJson(json);
        row.setSavedAt(Instant.now());
        VolunteerSavedCatEntity saved = volunteerSavedCatJpaRepository.save(row);
        return toSavedCatEntryDto(saved);
    }

    public List<VolunteerSavedCatEntryDto> listSavedCats(long userId) {
        loadVolunteer(userId);
        return volunteerSavedCatJpaRepository.findByUserIdOrderBySavedAtDesc(userId).stream()
                .map(this::toSavedCatEntryDto)
                .toList();
    }

    @Transactional
    public void deleteSavedCat(long userId, long catId) {
        loadVolunteer(userId);
        volunteerSavedCatJpaRepository.deleteByUserIdAndCatId(userId, catId);
    }

    private VolunteerSavedCatEntryDto toSavedCatEntryDto(VolunteerSavedCatEntity e) {
        try {
            JsonNode node = objectMapper.readTree(e.getSnapshotJson());
            return new VolunteerSavedCatEntryDto(e.getId(), e.getCatId(), e.getSavedAt(), node);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "invalid stored snapshot");
        }
    }

    private UserEntity loadVolunteer(long userId) {
        UserEntity u = userJpaRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        if (u.getRole() != UserRole.VOLUNTEER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not a volunteer account");
        }
        return u;
    }

    private VolunteerProfileResponse toProfile(UserEntity u, boolean includeStats) {
        VolunteerStatsResponse stats = includeStats ? getStatsOnly(u.getId()) : null;
        return new VolunteerProfileResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getPhone(),
                u.getBio(),
                u.getServiceLatitude(),
                u.getServiceLongitude(),
                u.isNotifyNearbyEnabled(),
                u.getVolunteerPoints(),
                u.getCreatedAt(),
                stats
        );
    }

    private static VolunteerRescueRecordDto toRescueRecord(SightingEntity s) {
        return new VolunteerRescueRecordDto(
                s.getId(),
                s.getCatId(),
                s.getOccurredAt(),
                s.getLatitude(),
                s.getLongitude(),
                s.getAddressText(),
                s.getDedupStatus().name()
        );
    }

    private void syncBadges(long userId) {
        syncBadgesReturningNew(userId);
    }

    /**
     * Idempotent badge grants from current counters / points / sightings.
     */
    @Transactional
    public List<VolunteerBadgeCode> syncBadgesReturningNew(long userId) {
        List<VolunteerBadgeCode> newly = new ArrayList<>();
        long checkins = volunteerFeedingCheckInJpaRepository.countByUserId(userId);
        long sightings = sightingJpaRepository.countByReporterUserId(userId);
        UserEntity u = userJpaRepository.findById(userId).orElseThrow();

        newly.addAll(tryAward(userId, VolunteerBadgeCode.FIRST_CHECKIN, checkins >= 1));
        newly.addAll(tryAward(userId, VolunteerBadgeCode.CHECKINS_10, checkins >= 10));
        newly.addAll(tryAward(userId, VolunteerBadgeCode.CHECKINS_50, checkins >= 50));
        newly.addAll(tryAward(userId, VolunteerBadgeCode.VOLUNTEER_POINTS_100, u.getVolunteerPoints() >= 100));
        newly.addAll(tryAward(userId, VolunteerBadgeCode.SIGHTINGS_5, sightings >= 5));
        return newly;
    }

    private List<VolunteerBadgeCode> tryAward(long userId, VolunteerBadgeCode code, boolean condition) {
        if (!condition) {
            return List.of();
        }
        if (volunteerBadgeEarnedJpaRepository.existsByUserIdAndBadgeCode(userId, code)) {
            return List.of();
        }
        VolunteerBadgeEarnedEntity e = new VolunteerBadgeEarnedEntity();
        e.setUserId(userId);
        e.setBadgeCode(code);
        e.setEarnedAt(Instant.now());
        volunteerBadgeEarnedJpaRepository.save(e);
        return List.of(code);
    }

    private static boolean isEffectivelySterilized(CatEntity cat) {
        if (cat.getSterilizationStatus() == SterilizationStatus.LIKELY_STERILIZED) {
            return true;
        }
        double conf = cat.getSterilizationConfidence() == null ? 0.0 : cat.getSterilizationConfidence();
        return cat.isEarTipped() && conf >= 0.60;
    }

    private static double priorityScore(CatEntity c) {
        double score = 50.0;
        if (c.getSterilizationStatus() == SterilizationStatus.NOT_STERILIZED) {
            score += 35;
        } else if (c.getSterilizationStatus() == SterilizationStatus.UNKNOWN) {
            score += 20;
        }
        if (!c.isEarTipped()) {
            score += 15;
        }
        return score;
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double rEarth = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1 - a)));
        return rEarth * c;
    }

    private String toPublicImageUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return value;
        }
        String path = value.startsWith("/") ? value : "/" + value;
        String configuredBase = siteProperties.getPublicBaseUrl() == null ? "" : siteProperties.getPublicBaseUrl().trim();
        if (configuredBase.isBlank()) {
            return path;
        }
        String base = configuredBase.endsWith("/") ? configuredBase.substring(0, configuredBase.length() - 1) : configuredBase;
        return base + path;
    }
}
