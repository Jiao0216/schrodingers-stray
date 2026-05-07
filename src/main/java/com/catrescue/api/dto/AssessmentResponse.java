package com.catrescue.api.dto;

import com.catrescue.api.domain.AssessmentStatus;
import com.catrescue.api.domain.BranchType;

import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        AssessmentStatus status,
        BranchType branchType,
        ModelLabelsDto modelLabels,
    /** Audit only; original filename from upload. */
    String originalFilename,
    String contentType,
    /** True when the upload bytes were stored (see cat-rescue.assessment.persist-images). */
    boolean imagePersisted,
        Double latitude,
        Double longitude,
        String addressText,
        BranchActionsDto actions,
        String failureReason,
        /** Present when {@code reporterUserId} was sent with the assessment and sighting ingest succeeded. */
        AssessmentTrackingMatchDto trackingMatch,
        /** Prior sighting match when feature similarity &gt; 70%. */
        AssessmentDuplicateMatchDto assessmentDuplicate
) {
}
