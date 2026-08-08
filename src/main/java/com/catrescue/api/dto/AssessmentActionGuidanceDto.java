package com.catrescue.api.dto;

import com.catrescue.api.tracking.dto.FeedingSpotResponse;
import com.catrescue.api.tracking.dto.ShelterInfoResponse;

import java.util.List;

/**
 * Post-upload action guidance shown immediately after photo analysis.
 */
public record AssessmentActionGuidanceDto(
        String userEarClip,
        boolean showTnrNearby,
        List<ShelterInfoResponse> tnrSheltersNearby,
        boolean showFeedingSpotPrompt,
        List<FeedingSpotResponse> feedingSpotsNearby,
        String bowlPurchaseSearchUrl,
        List<String> guidanceLines
) {
}
