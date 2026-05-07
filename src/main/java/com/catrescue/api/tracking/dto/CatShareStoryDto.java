package com.catrescue.api.tracking.dto;

import java.util.List;

/**
 * Client-ready payload for Web Share API / social paste — no OAuth required.
 */
public record CatShareStoryDto(
        String title,
        String description,
        String url,
        List<String> hashtags,
        String shareTextZh,
        String shareTextEn
) {
}
