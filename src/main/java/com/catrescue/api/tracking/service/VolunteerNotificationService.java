package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.domain.NotificationType;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.notify.ExternalNotificationDispatcher;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.SightingEntity;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.persistence.VolunteerNotificationEntity;
import com.catrescue.api.tracking.dto.VolunteerNotificationResponse;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.SightingJpaRepository;
import com.catrescue.api.tracking.repository.UserJpaRepository;
import com.catrescue.api.tracking.repository.VolunteerNotificationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-app volunteer notifications. Proximity alerts follow TrackingSightingRecordedEvent; absence reminders follow CatAbsenceThresholdEvent.
 */
@Service
public class VolunteerNotificationService {

    /** Same spatial scale as deduplication for consistency with product wording. */
    public static final double NEARBY_VOLUNTEER_RADIUS_METERS = 500.0;

    public static final int ABSENCE_REMINDER_DAYS = 7;

    private final SightingJpaRepository sightingJpaRepository;
    private final CatJpaRepository catJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final VolunteerNotificationJpaRepository volunteerNotificationJpaRepository;
    private final ExternalNotificationDispatcher externalNotificationDispatcher;

    public VolunteerNotificationService(
            SightingJpaRepository sightingJpaRepository,
            CatJpaRepository catJpaRepository,
            UserJpaRepository userJpaRepository,
            VolunteerNotificationJpaRepository volunteerNotificationJpaRepository,
            ExternalNotificationDispatcher externalNotificationDispatcher
    ) {
        this.sightingJpaRepository = sightingJpaRepository;
        this.catJpaRepository = catJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.volunteerNotificationJpaRepository = volunteerNotificationJpaRepository;
        this.externalNotificationDispatcher = externalNotificationDispatcher;
    }

    public List<VolunteerNotificationResponse> listForVolunteer(Long volunteerUserId) {
        return volunteerNotificationJpaRepository.findByVolunteerUserIdOrderByCreatedAtDesc(volunteerUserId).stream()
                .map(VolunteerNotificationService::toDto)
                .toList();
    }

    @Transactional
    public void acknowledge(long volunteerUserId, long notificationId) {
        VolunteerNotificationEntity n = volunteerNotificationJpaRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("notification not found"));
        if (!Objects.equals(n.getVolunteerUserId(), volunteerUserId)) {
            throw new IllegalArgumentException("notification not owned by user");
        }
        n.setAcknowledged(true);
        volunteerNotificationJpaRepository.save(n);
    }

    /**
     * Invoked after commit when a sighting is stored: notifies subscribed volunteers near the coordinates
     * if the inferred cat is not already treated as sterilized.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyVolunteersForUnneuteredSighting(Long sightingId) {
        SightingEntity sighting = sightingJpaRepository.findById(sightingId)
                .orElseThrow(() -> new IllegalArgumentException("Sighting not found: " + sightingId));
        Long catLookupId = sighting.getCatId() != null ? sighting.getCatId() : sighting.getSuggestedCatId();
        if (catLookupId == null) {
            return;
        }
        CatEntity cat = catJpaRepository.findById(catLookupId).orElse(null);
        if (cat == null) {
            return;
        }
        if (isEffectivelySterilized(cat, sighting)) {
            return;
        }

        List<UserEntity> volunteers = userJpaRepository.findVolunteersWithServicePointWithinMeters(
                sighting.getLatitude(),
                sighting.getLongitude(),
                NEARBY_VOLUNTEER_RADIUS_METERS
        );
        for (UserEntity volunteer : volunteers) {
            if (volunteer.getRole() != UserRole.VOLUNTEER) {
                continue;
            }
            if (!volunteer.isNotifyNearbyEnabled()) {
                continue;
            }
            if (Objects.equals(volunteer.getId(), sighting.getReporterUserId())) {
                continue;
            }
            if (volunteerNotificationJpaRepository.existsByVolunteerUserIdAndRelatedSightingId(
                    volunteer.getId(),
                    sighting.getId()
            )) {
                continue;
            }
            VolunteerNotificationEntity n = new VolunteerNotificationEntity();
            n.setVolunteerUserId(volunteer.getId());
            n.setType(NotificationType.UNNEUTERED_SIGHTING_NEARBY);
            n.setTitle("New un-neutered stray sighting nearby");
            n.setBody(String.format(
                    "A sighting (id=%s) was reported within 500 m of your service point for cat id=%s. Coordinates: %.5f, %.5f.",
                    sighting.getId(),
                    cat.getId(),
                    sighting.getLatitude(),
                    sighting.getLongitude()
            ));
            n.setPayloadJson("{\"sightingId\":" + sighting.getId() + ",\"catId\":" + cat.getId() + "}");
            n.setRelatedCatId(cat.getId());
            n.setRelatedSightingId(sighting.getId());
            n.setCreatedAt(Instant.now());
            VolunteerNotificationEntity saved = volunteerNotificationJpaRepository.save(n);
            externalNotificationDispatcher.dispatchExternal(saved);
        }
    }

    /**
     * Invoked after commit when the scheduler detects a stale cat profile.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyLastReporterCatAbsent(Long catId) {
        Instant cutoff = Instant.now().minus(ABSENCE_REMINDER_DAYS, ChronoUnit.DAYS);
        CatEntity cat = catJpaRepository.findById(catId).orElse(null);
        if (cat == null) {
            return;
        }
        if (cat.getAbsenceReminderSentAt() != null) {
            return;
        }
        if (!cat.getLastSeenAt().isBefore(cutoff)) {
            return;
        }
        Optional<SightingEntity> latest = sightingJpaRepository.findFirstByCatIdOrderByOccurredAtDesc(catId);
        Long recipient = latest.map(SightingEntity::getReporterUserId).orElse(cat.getCreatedByUserId());
        if (recipient == null) {
            return;
        }
        Instant sentAt = Instant.now();
        int claimed = catJpaRepository.claimAbsenceReminderSlot(catId, cutoff, sentAt);
        if (claimed == 0) {
            return;
        }
        VolunteerNotificationEntity n = new VolunteerNotificationEntity();
        n.setVolunteerUserId(recipient);
        n.setType(NotificationType.CAT_ABSENCE_REMINDER);
        n.setTitle("Cat not re-sighted for 7+ days");
        n.setBody(String.format(
                "Cat id=%s (%s) has had no new sightings since %s. Consider a welfare check if still relevant.",
                cat.getId(),
                Optional.ofNullable(cat.getDisplayName()).orElse("unnamed"),
                cat.getLastSeenAt()
        ));
        n.setPayloadJson("{\"catId\":" + cat.getId() + "}");
        n.setRelatedCatId(cat.getId());
        n.setRelatedSightingId(null);
        n.setCreatedAt(Instant.now());
        VolunteerNotificationEntity saved = volunteerNotificationJpaRepository.save(n);
        externalNotificationDispatcher.dispatchExternal(saved);
    }

    private static boolean isEffectivelySterilized(CatEntity cat, SightingEntity sighting) {
        if (cat.getSterilizationStatus() == SterilizationStatus.LIKELY_STERILIZED) {
            return true;
        }
        if (cat.isEarTipped() && confidence(cat.getSterilizationConfidence()) >= 0.60) {
            return true;
        }
        return sighting.isEarTipped() && sighting.getEarTippedConfidence() >= 0.60;
    }

    private static double confidence(Double v) {
        return v == null ? 0.0 : v;
    }

    private static VolunteerNotificationResponse toDto(VolunteerNotificationEntity e) {
        return new VolunteerNotificationResponse(
                e.getId(),
                e.getType(),
                e.getTitle(),
                e.getBody(),
                e.getPayloadJson(),
                e.isAcknowledged(),
                e.getRelatedCatId(),
                e.getRelatedSightingId(),
                e.getCreatedAt()
        );
    }
}
