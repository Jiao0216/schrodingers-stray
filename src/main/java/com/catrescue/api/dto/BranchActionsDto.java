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
        List<String> healthGuidanceLines,
        /* Simplified Chinese versions of the fields above (the defaults are English). */
        String disclaimerZh,
        List<String> rescueNextStepsZh,
        String nextdoorTitleZh,
        String nextdoorBodyZh,
        List<String> healthGuidanceLinesZh
) {
}
