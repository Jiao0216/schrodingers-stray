package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.CatFeatureVector;

public interface CatFeatureExtractionService {

    CatFeatureVector extractFromImage(String imageUrlOrDataUrl);
}
