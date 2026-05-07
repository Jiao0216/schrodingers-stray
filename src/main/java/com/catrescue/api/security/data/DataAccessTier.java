package com.catrescue.api.security.data;

/**
 * Tier for coarse-grained data exposure on public HTTP APIs.
 * <ul>
 *     <li>{@link #PUBLIC} — C-end / anonymous: fuzzy coordinates, redacted health details.</li>
 *     <li>{@link #INSTITUTION} — B-end: full precision when caller presents the institution data token.</li>
 *     <li>{@link #ADMIN} — operations / full precision.</li>
 * </ul>
 */
public enum DataAccessTier {
    PUBLIC,
    INSTITUTION,
    ADMIN
}
