package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.CatFeatureVector;

public interface CatFeatureExtractionService {

    CatFeatureVector extractFromImage(String imageUrlOrDataUrl);

    /**
     * Same as {@link #extractFromImage(String)}, plus an optional JPEG close-up of the cat's head
     * used as extra ear-tip evidence. Implementations that cannot use it ignore it.
     */
    default CatFeatureVector extractFromImage(String imageUrlOrDataUrl, byte[] headCropJpeg) {
        return extractFromImage(imageUrlOrDataUrl);
    }
}
