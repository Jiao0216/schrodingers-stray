package com.catrescue.api.tracking.controller;

import com.catrescue.api.tracking.dto.CreateFeedingRecordRequest;
import com.catrescue.api.tracking.dto.FeedingRecordResponse;
import com.catrescue.api.tracking.dto.OverdueCatResponse;
import com.catrescue.api.tracking.service.FeedingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/cats", "/cats"})
public class CatFeedingController {

    private final FeedingService feedingService;

    public CatFeedingController(FeedingService feedingService) {
        this.feedingService = feedingService;
    }

    /**
     * Cats with no feeding entry in the last {@code hours} hours (default 24), including never fed.
     */
    @GetMapping("/feeding/overdue-cats")
    public List<OverdueCatResponse> overdueCats(
            @RequestParam(name = "hours", defaultValue = "24") int hours
    ) {
        return feedingService.listCatsWithoutRecentFeeding(hours);
    }

    @PostMapping("/{catId}/feeding-records")
    public ResponseEntity<FeedingRecordResponse> create(
            @PathVariable("catId") long catId,
            @RequestBody(required = false) CreateFeedingRecordRequest body
    ) {
        return feedingService.recordFeeding(catId, body)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{catId}/feeding-records")
    public List<FeedingRecordResponse> list(@PathVariable("catId") long catId) {
        return feedingService.listForCat(catId);
    }
}
