package com.catrescue.api.tracking.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published when a cat profile has exceeded the configured absence window without a new sighting.
 * Used to remind the volunteer who last reported that cat.
 */
public class CatAbsenceThresholdEvent extends ApplicationEvent {

    private final Long catId;

    public CatAbsenceThresholdEvent(Object source, Long catId) {
        super(source);
        this.catId = catId;
    }

    public Long getCatId() {
        return catId;
    }
}
