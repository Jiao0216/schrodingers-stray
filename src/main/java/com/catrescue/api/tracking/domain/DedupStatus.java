package com.catrescue.api.tracking.domain;

public enum DedupStatus {
    NEW_CONFIRMED,
    PENDING_REVIEW,
    CONFIRMED_DUPLICATE,
    REJECTED_DUPLICATE,
    DUPLICATE_SUSPECTED,
    NEW_CAT
}
