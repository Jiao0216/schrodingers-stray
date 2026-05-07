package com.catrescue.api.domain;

public enum BranchType {
    /** Suspected illness or injury — escalate to rescue / vet */
    RESCUE,
    /** Stable cat, community feeding coordination (e.g. Nextdoor copy) */
    FEEDING_OUTREACH,
    /** Suspected not neutered / ear not tipped — TNR resources */
    TNR_RESOURCES,
    /** Low confidence or no strong signal */
    GENERAL_GUIDANCE
}
