package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.CatPhotoMatchResponse;
import com.catrescue.api.tracking.service.CatPhotoMatchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/v1/cats", "/cats"})
public class CatPhotoMatchController {

    private final CatPhotoMatchService catPhotoMatchService;

    public CatPhotoMatchController(CatPhotoMatchService catPhotoMatchService) {
        this.catPhotoMatchService = catPhotoMatchService;
    }

    /**
     * Multipart field name: {@code file}. Uses the same multimodal feature extraction + similarity weights as sighting dedup.
     */
    @PostMapping(value = "/match-by-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatPhotoMatchResponse> matchByPhoto(@RequestPart("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(catPhotoMatchService.matchByPhoto(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
