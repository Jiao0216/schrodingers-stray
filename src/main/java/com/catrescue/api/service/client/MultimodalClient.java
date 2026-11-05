package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

import java.util.Optional;

/**
 * Calls your multimodal model (hosted HTTP or sidecar). Replace {@link StubMultimodalClient} in prod.
 */
public interface MultimodalClient {

    ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType);

    /**
     * Same as {@link #analyzeImageBytes(byte[], String)}, plus an optional JPEG close-up of the cat's head
     * (from the embedding-service detector) used as extra ear-tip evidence. Clients that cannot use it ignore it.
     */
    default ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType, byte[] headCropJpeg) {
        return analyzeImageBytes(imageBytes, contentType);
    }

    /**
     * Dedicated ear-tip check only (no health analysis), used by the evaluation endpoint to compare settings.
     * {@code votes} / {@code model} override the configured values when non-null. Empty when unsupported or all
     * calls failed.
     */
    default Optional<EarTipJudgement> judgeEarTip(
            byte[] imageBytes, String contentType, byte[] headCropJpeg, Integer votes, String model
    ) {
        return Optional.empty();
    }
}
