package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.FeedingSpotCheckInRequest;
import com.catrescue.api.tracking.dto.FeedingSpotCheckInResponse;
import com.catrescue.api.tracking.dto.FeedingSpotDetailResponse;
import com.catrescue.api.tracking.dto.FeedingSpotResponse;
import com.catrescue.api.tracking.service.FeedingSpotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/feeding-spots", "/feeding-spots"})
public class FeedingSpotController {

    private final FeedingSpotService feedingSpotService;

    public FeedingSpotController(FeedingSpotService feedingSpotService) {
        this.feedingSpotService = feedingSpotService;
    }

    @GetMapping
    public List<FeedingSpotResponse> list(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radiusM", defaultValue = "800") double radiusM,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        if (lat != null && lng != null) {
            return feedingSpotService.nearestActive(lat, lng, radiusM, limit);
        }
        return feedingSpotService.listActive();
    }

    @GetMapping("/{id}")
    public FeedingSpotDetailResponse detail(
            @PathVariable("id") long id,
            @RequestParam(value = "recordLimit", defaultValue = "20") int recordLimit
    ) {
        return feedingSpotService.getDetail(id, recordLimit);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("id") long id) {
        byte[] bytes = feedingSpotService.getPhotoBytes(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        String ct = feedingSpotService.getPhotoContentType(id).orElse(null);
        if (ct != null && !ct.isBlank()) {
            try {
                type = MediaType.parseMediaType(ct);
            } catch (Exception ignored) {
                // keep default
            }
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FeedingSpotResponse create(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId
    ) {
        return feedingSpotService.create(photo, latitude, longitude, description, createdByUserId);
    }

    @PostMapping("/{id}/check-ins")
    public FeedingSpotCheckInResponse checkIn(
            @PathVariable("id") long id,
            @RequestBody(required = false) FeedingSpotCheckInRequest body
    ) {
        Long fedBy = body != null ? body.fedByUserId() : null;
        String note = body != null ? body.note() : null;
        return feedingSpotService.checkIn(id, fedBy, note);
    }
}
