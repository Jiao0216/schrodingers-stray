package com.catrescue.api.tracking.schedule;

import com.catrescue.api.tracking.event.CatAbsenceThresholdEvent;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.service.VolunteerNotificationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Periodically scans for cats that have not been re-sighted for ABSENCE_REMINDER_DAYS
 * and emits CatAbsenceThresholdEvent for each candidate.
 */
@Component
public class CatAbsenceScheduler {

    private final CatJpaRepository catJpaRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CatAbsenceScheduler(CatJpaRepository catJpaRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.catJpaRepository = catJpaRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Runs inside a short transaction so {@link org.springframework.transaction.event.TransactionalEventListener}
     * (AFTER_COMMIT) has a completed boundary before handlers execute.
     */
    @Scheduled(fixedRateString = "${cat-rescue.tracking.absence-scan-ms:3600000}")
    @Transactional
    public void scanAndPublishAbsenceEvents() {
        Instant cutoff = Instant.now().minus(VolunteerNotificationService.ABSENCE_REMINDER_DAYS, ChronoUnit.DAYS);
        List<CatEntity> due = catJpaRepository.findByLastSeenAtBeforeAndAbsenceReminderSentAtIsNull(cutoff);
        for (CatEntity cat : due) {
            applicationEventPublisher.publishEvent(new CatAbsenceThresholdEvent(this, cat.getId()));
        }
    }
}
