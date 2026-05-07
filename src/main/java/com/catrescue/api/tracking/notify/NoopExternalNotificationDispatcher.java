package com.catrescue.api.tracking.notify;

import com.catrescue.api.tracking.persistence.VolunteerNotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder until SMTP/SMS integrations are configured.
 */
@Component
public class NoopExternalNotificationDispatcher implements ExternalNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NoopExternalNotificationDispatcher.class);

    @Override
    public void dispatchExternal(VolunteerNotificationEntity notification) {
        log.debug(
                "External channel reserved: notification id={} type={} userId={}",
                notification.getId(),
                notification.getType(),
                notification.getVolunteerUserId()
        );
    }
}
