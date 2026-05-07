package com.catrescue.api.tracking.dto;

import java.util.List;

public record CatPhotoMatchResponse(List<CatPhotoMatchItemResponse> matches) {
}
