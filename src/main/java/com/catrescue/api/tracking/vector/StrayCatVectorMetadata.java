package com.catrescue.api.tracking.vector;

/**
 * Metadata stored alongside a CLIP vector in Chroma.
 */
public record StrayCatVectorMetadata(
        long sightingId,
        Long catId,
        String uploadTime,
        String locationApprox,
        String imageUrl
) {
}
