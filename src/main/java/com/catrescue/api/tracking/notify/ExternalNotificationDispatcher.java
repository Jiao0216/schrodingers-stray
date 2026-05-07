package com.catrescue.api.tracking.notify;

import com.catrescue.api.tracking.persistence.VolunteerNotificationEntity;

/**
 * Extension point for email/SMS/push providers. Default implementation is a no-op logger.
 */
public interface ExternalNotificationDispatcher {

    /**
     * Called after an in-app notification row is committed. Implementations may enqueue
     * third-party delivery without blocking the HTTP request.
     *
     * @param notification persisted entity (id assigned)
     */
    void dispatchExternal(VolunteerNotificationEntity notification);
}
