package com.catrescue.api.tracking.dto;

import java.util.List;

public record CatLookupResponse(List<CatLookupHitDto> matches) {
}
