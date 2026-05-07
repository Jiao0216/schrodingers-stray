package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;

/**
 * Calls your multimodal model (hosted HTTP or sidecar). Replace {@link StubMultimodalClient} in prod.
 */
public interface MultimodalClient {

    ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType);
}
