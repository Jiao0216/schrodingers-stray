package com.catrescue.api.dto;

import com.catrescue.api.tracking.dto.FeedingSpotResponse;
import com.catrescue.api.tracking.dto.ShelterInfoResponse;

import java.util.List;

/**
 * Post-upload action guidance shown immediately after photo analysis.
 */
public record AssessmentActionGuidanceDto(
        /** Effective ear-tip status the guidance follows: "yes" | "no" | "unclear". */
        String userEarClip,
        /** "user" when the volunteer answered, "ai" when it was left to the AI ear-tip check. */
        String earClipSource,
        boolean showTnrNearby,
        List<ShelterInfoResponse> tnrSheltersNearby,
        boolean showFeedingSpotPrompt,
        List<FeedingSpotResponse> feedingSpotsNearby,
        String bowlPurchaseSearchUrl,
        List<String> guidanceLines
) {
}
