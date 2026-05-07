package com.catrescue.api.tracking.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published after a sighting row is persisted. Listeners run after transaction commit
 * so downstream queries see consistent data.
 */
public class TrackingSightingRecordedEvent extends ApplicationEvent {

    private final Long sightingId;

    public TrackingSightingRecordedEvent(Object source, Long sightingId) {
        super(source);
        this.sightingId = sightingId;
    }

    public Long getSightingId() {
        return sightingId;
    }
}
