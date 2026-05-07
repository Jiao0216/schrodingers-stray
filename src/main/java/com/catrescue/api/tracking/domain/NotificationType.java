package com.catrescue.api.tracking.domain;

/**
 * In-app notification categories for volunteers.
 * Additional channels (email/SMS) can map from these types later.
 */
public enum NotificationType {
    /** A new sighting of an un-neutered cat was reported near the volunteer's subscribed coordinates. */
    UNNEUTERED_SIGHTING_NEARBY,
    /** A cat the volunteer last reported has had no new sightings for more than 7 days. */
    CAT_ABSENCE_REMINDER
}
