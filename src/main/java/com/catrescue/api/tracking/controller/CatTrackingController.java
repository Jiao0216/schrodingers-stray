package com.catrescue.api.tracking.controller;

import com.catrescue.api.security.data.DataAccessResolver;
import com.catrescue.api.security.data.DataAccessTier;
import com.catrescue.api.security.data.DataMaskingService;
import com.catrescue.api.tracking.dto.CatLastSeenResponse;
import com.catrescue.api.tracking.dto.CatProfileResponse;
import com.catrescue.api.tracking.dto.HeatmapPointDto;
import com.catrescue.api.tracking.dto.HeatmapSampleResponse;
import com.catrescue.api.tracking.dto.HeatmapSightingResponse;
import com.catrescue.api.tracking.service.CatHeatmapService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read APIs for cat movement history (heatmap + last seen).
 * Coordinate precision and profile fields depend on {@link DataAccessTier} (see headers on {@link DataAccessResolver}).
 */
@RestController
@RequestMapping({"/api/v1/cats", "/cats"})
public class CatTrackingController {

    private final CatHeatmapService catHeatmapService;
    private final DataAccessResolver dataAccessResolver;
    private final DataMaskingService dataMaskingService;

    public CatTrackingController(
            CatHeatmapService catHeatmapService,
            DataAccessResolver dataAccessResolver,
            DataMaskingService dataMaskingService
    ) {
        this.catHeatmapService = catHeatmapService;
        this.dataAccessResolver = dataAccessResolver;
        this.dataMaskingService = dataMaskingService;
    }

    @GetMapping("/heatmap")
    public List<HeatmapSampleResponse> globalHeatmap(HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        List<HeatmapSampleResponse> raw = catHeatmapService.buildGlobalHeatmap().points().stream()
                .map(this::toSample)
                .toList();
        return dataMaskingService.maskHeatmapPoints(raw, tier);
    }

    @GetMapping("/heatmap/sightings")
    public List<HeatmapSightingResponse> globalHeatmapSightings(HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        List<HeatmapSightingResponse> raw = catHeatmapService.buildGlobalHeatmapSightings();
        return dataMaskingService.maskHeatmapSightings(raw, tier);
    }

    @GetMapping("/{id}/heatmap")
    public List<HeatmapSampleResponse> heatmap(@PathVariable("id") Long catId, HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        List<HeatmapSampleResponse> raw = catHeatmapService.buildHeatmap(catId).points().stream()
                .map(this::toSample)
                .toList();
        return dataMaskingService.maskHeatmapPoints(raw, tier);
    }

    @GetMapping("/{id}/heatmap/sightings")
    public List<HeatmapSightingResponse> heatmapSightings(@PathVariable("id") Long catId, HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        List<HeatmapSightingResponse> raw = catHeatmapService.buildHeatmapSightings(catId);
        return dataMaskingService.maskHeatmapSightings(raw, tier);
    }

    /**
     * Full profile for B/Admin and for the viewer's own contributions; otherwise a public summary.
     *
     * @param viewerUserId optional reporter/volunteer id — when it matches a contributor to this cat, full detail is returned.
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<CatProfileResponse> profile(
            @PathVariable("id") Long catId,
            @RequestParam(name = "viewerUserId", required = false) Long viewerUserId,
            HttpServletRequest request
    ) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        if (tier == DataAccessTier.ADMIN || tier == DataAccessTier.INSTITUTION) {
            return catHeatmapService.buildProfile(catId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        if (viewerUserId != null && catHeatmapService.userContributedToCat(catId, viewerUserId)) {
            return catHeatmapService.buildProfile(catId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        return catHeatmapService.buildPublicProfile(catId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/last-seen")
    public CatLastSeenResponse lastSeen(@PathVariable("id") Long catId, HttpServletRequest request) {
        DataAccessTier tier = dataAccessResolver.resolve(request);
        CatLastSeenResponse raw = catHeatmapService.buildLastSeen(catId);
        return dataMaskingService.maskLastSeen(raw, tier);
    }

    private HeatmapSampleResponse toSample(HeatmapPointDto p) {
        return new HeatmapSampleResponse(p.latitude(), p.longitude(), p.weight());
    }
}
