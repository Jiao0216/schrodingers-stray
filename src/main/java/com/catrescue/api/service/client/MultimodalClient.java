package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

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
}
