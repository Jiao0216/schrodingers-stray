package com.catrescue.api.tracking.event;

import com.catrescue.api.tracking.service.VolunteerNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges domain events to {@link VolunteerNotificationService} after the originating transaction commits,
 * so notification queries always see persisted sightings/cats.
 */
@Component
public class TrackingNotificationEventListener {

    private final VolunteerNotificationService volunteerNotificationService;

    public TrackingNotificationEventListener(VolunteerNotificationService volunteerNotificationService) {
        this.volunteerNotificationService = volunteerNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSightingRecorded(TrackingSightingRecordedEvent event) {
        volunteerNotificationService.notifyVolunteersForUnneuteredSighting(event.getSightingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCatAbsenceThreshold(CatAbsenceThresholdEvent event) {
        volunteerNotificationService.notifyLastReporterCatAbsent(event.getCatId());
    }
}
