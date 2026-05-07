package com.catrescue.api.dto;

/**
 * Binary payload for {@code GET /api/v1/assessments/{id}/image}.
 */
public record StoredAssessmentImage(byte[] bytes, String contentType) {}
