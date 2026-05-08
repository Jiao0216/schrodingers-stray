package com.catrescue.api.tracking.controller;

import com.catrescue.api.security.data.DataAccessResolver;
import com.catrescue.api.security.data.DataMaskingService;
import com.catrescue.api.tracking.dto.CatShareStoryDto;
import com.catrescue.api.tracking.dto.FeedingCheckInRequest;
import com.catrescue.api.tracking.dto.FeedingCheckInResponse;
import com.catrescue.api.tracking.dto.NearbyHelpCatDto;
import com.catrescue.api.tracking.dto.PatchVolunteerProfileRequest;
import com.catrescue.api.tracking.dto.RegisterVolunteerRequest;
import com.catrescue.api.tracking.dto.SaveVolunteerCatRequest;
import com.catrescue.api.tracking.dto.VolunteerBadgeDto;
import com.catrescue.api.tracking.dto.VolunteerCreatedCatDto;
import com.catrescue.api.tracking.dto.VolunteerLeaderboardEntryDto;
import com.catrescue.api.tracking.dto.VolunteerLatestCatResponse;
import com.catrescue.api.tracking.dto.VolunteerProfileResponse;
import com.catrescue.api.tracking.dto.VolunteerRescueRecordDto;
import com.catrescue.api.tracking.dto.VolunteerSavedCatEntryDto;
import com.catrescue.api.tracking.dto.VolunteerStatsResponse;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.service.VolunteerHubService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@RestController
@Validated
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/volunteers")
public class VolunteerHubController {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VolunteerHubService volunteerHubService;
    private final DataAccessResolver dataAccessResolver;
    private final DataMaskingService dataMaskingService;

    public VolunteerHubController(
            VolunteerHubService volunteerHubService,
            DataAccessResolver dataAccessResolver,
            DataMaskingService dataMaskingService
    ) {
        this.volunteerHubService = volunteerHubService;
        this.dataAccessResolver = dataAccessResolver;
        this.dataMaskingService = dataMaskingService;
    }

    @PostMapping("/register")
    public ResponseEntity<VolunteerProfileResponse> register(@Valid @RequestBody RegisterVolunteerRequest body) {
        UserEntity created = volunteerHubService.register(body);
        VolunteerProfileResponse profile = volunteerHubService.getProfile(created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(profile);
    }

    @GetMapping("/profile")
    public VolunteerProfileResponse profile(@RequestParam @Positive long userId) {
        return volunteerHubService.getProfile(userId);
    }

    @GetMapping({"/me", "/me/"})
    public VolunteerProfileResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        long userId = parseUserIdFromToken(authorization);
        return volunteerHubService.getProfile(userId);
    }

    @PatchMapping("/profile")
    public VolunteerProfileResponse patchProfile(
            @RequestParam @Positive long userId,
            @Valid @RequestBody PatchVolunteerProfileRequest body
    ) {
        return volunteerHubService.patchProfile(userId, body);
    }

    @GetMapping("/me/rescue-records")
    public List<VolunteerRescueRecordDto> rescueRecords(
            @RequestParam @Positive long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return volunteerHubService.listRescueRecords(userId, page, size);
    }

    @GetMapping({"/me/cats-created", "/me/cats-created/"})
    public List<VolunteerCreatedCatDto> catsCreatedByVolunteer(@RequestParam @Positive long userId) {
        return volunteerHubService.listCatsCreatedByVolunteer(userId);
    }

    @PostMapping({"/me/saved-cats", "/me/saved-cats/"})
    public VolunteerSavedCatEntryDto saveCatToHub(
            @RequestParam @Positive long userId,
            @Valid @RequestBody SaveVolunteerCatRequest body
    ) {
        return volunteerHubService.saveCatSnapshot(userId, body.catId());
    }

    @GetMapping({"/me/saved-cats", "/me/saved-cats/"})
    public List<VolunteerSavedCatEntryDto> listSavedCats(@RequestParam @Positive long userId) {
        return volunteerHubService.listSavedCats(userId);
    }

    @DeleteMapping("/me/saved-cats/{catId}")
    public ResponseEntity<Void> deleteSavedCat(
            @RequestParam @Positive long userId,
            @PathVariable @Positive long catId
    ) {
        volunteerHubService.deleteSavedCat(userId, catId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/stats")
    public VolunteerStatsResponse stats(@RequestParam @Positive long userId) {
        return volunteerHubService.getStatsOnly(userId);
    }

    @GetMapping("/me/latest-cat")
    public ResponseEntity<VolunteerLatestCatResponse> latestCat(@RequestParam @Positive long userId) {
        Optional<VolunteerLatestCatResponse> row = volunteerHubService.latestUploadedCat(userId);
        return row.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/me/nearby-help-cats")
    public List<NearbyHelpCatDto> nearbyHelpCats(
            @RequestParam @Positive long userId,
            HttpServletRequest request
    ) {
        return dataMaskingService.maskNearbyCats(
                volunteerHubService.nearbyCatsNeedingHelp(userId),
                dataAccessResolver.resolve(request)
        );
    }

    @PostMapping("/me/feeding-checkins")
    public FeedingCheckInResponse feedingCheckIn(
            @RequestParam @Positive long userId,
            @Valid @RequestBody FeedingCheckInRequest body
    ) {
        return volunteerHubService.feedingCheckIn(userId, body);
    }

    @GetMapping("/me/badges")
    public List<VolunteerBadgeDto> badges(@RequestParam @Positive long userId) {
        return volunteerHubService.listBadges(userId);
    }

    @GetMapping("/leaderboard")
    public List<VolunteerLeaderboardEntryDto> leaderboard(
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit
    ) {
        return volunteerHubService.leaderboard(limit);
    }

    @GetMapping("/share/cat-story")
    public CatShareStoryDto shareCatStory(
            @RequestParam @Positive long catId,
            @RequestParam(required = false) Long userId
    ) {
        return volunteerHubService.buildCatShareStory(catId, userId);
    }

    private long parseUserIdFromToken(String authorization) {
        String raw = authorization == null ? "" : authorization.trim();
        if (raw.isBlank() || !raw.toLowerCase().startsWith("bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = raw.substring(7).trim();
        if (token.startsWith("cg_")) {
            String[] parts = token.split("_");
            if (parts.length < 3) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token format");
            }
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token user id");
            }
        }
        if (token.matches("^[1-9]\\d*$")) {
            return Long.parseLong(token);
        }
        try {
            String[] jwtParts = token.split("\\.");
            if (jwtParts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(jwtParts[1]);
                JsonNode payload = OBJECT_MAPPER.readTree(decoded);
                if (payload.hasNonNull("userId")) {
                    return payload.get("userId").asLong();
                }
                if (payload.hasNonNull("uid")) {
                    return payload.get("uid").asLong();
                }
                if (payload.hasNonNull("sub")) {
                    String sub = payload.get("sub").asText();
                    if (sub != null && sub.matches("^[1-9]\\d*$")) {
                        return Long.parseLong(sub);
                    }
                }
            }
        } catch (Exception _e) {
            // ignore and throw below
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
    }
}
