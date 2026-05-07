package com.catrescue.api.dto;

import java.util.List;

public record BranchActionsDto(
        String disclaimer,
        List<String> rescueNextSteps,
        String nextdoorTitle,
        String nextdoorBody,
        List<TnrLocationDto> rescueNearby,
        List<TnrLocationDto> tnrNearby,
        /** Model-driven health / referral lines (shown above next steps). */
        List<String> healthGuidanceLines
) {
}
