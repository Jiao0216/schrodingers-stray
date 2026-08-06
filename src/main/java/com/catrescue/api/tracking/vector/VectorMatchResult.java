package com.catrescue.api.tracking.vector;

/**
 * One nearest-neighbor hit from embedding-service {@code POST /chroma/query}.
 */
public record VectorMatchResult(
        String id,
        long sightingId,
        Long catId,
        double distance,
        String uploadTime,
        String locationApprox,
        String imageUrl
) {
}
