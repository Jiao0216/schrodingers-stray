package com.catrescue.api.tracking.service;

import com.catrescue.api.config.EmbeddingServiceProperties;
import com.catrescue.api.service.client.ClipEmbeddingClient;
import com.catrescue.api.tracking.persistence.CatMatchFeedbackEntity;
import com.catrescue.api.tracking.repository.CatMatchFeedbackJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Serves the CLIP same-cat distance threshold, re-fitted from volunteer confirm/reject feedback.
 * Only feedback where the volunteer judged CLIP's own candidate under the current embedding version is used,
 * so labels from feature-only matches or from older (uncropped) embeddings never skew the threshold.
 */
@Service
public class ClipThresholdCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(ClipThresholdCalibrationService.class);

    public record Status(
            double configuredThreshold,
            double effectiveThreshold,
            boolean autoCalibrate,
            String embeddingVersion,
            Instant computedAt,
            ClipThresholdCalibrator.Result result
    ) {
    }

    private final CatMatchFeedbackJpaRepository feedbackRepository;
    private final ClipEmbeddingClient clipEmbeddingClient;
    private final EmbeddingServiceProperties props;

    private volatile Status status;

    public ClipThresholdCalibrationService(
            CatMatchFeedbackJpaRepository feedbackRepository,
            ClipEmbeddingClient clipEmbeddingClient,
            EmbeddingServiceProperties props
    ) {
        this.feedbackRepository = feedbackRepository;
        this.clipEmbeddingClient = clipEmbeddingClient;
        this.props = props;
    }

    /** Threshold to compare CLIP distances against (calibrated when enough labels exist, else configured). */
    public double effectiveThreshold() {
        if (!props.isAutoCalibrate()) {
            return props.getDistanceThreshold();
        }
        Status s = status;
        // Version is learned from /embed responses; refit as soon as it is known or changes.
        if (s == null || !Objects.equals(s.embeddingVersion(), clipEmbeddingClient.embeddingVersion())) {
            s = recalibrate();
        }
        return s.effectiveThreshold();
    }

    public Status status() {
        Status s = status;
        return s != null ? s : recalibrate();
    }

    @Scheduled(
            initialDelayString = "${cat-rescue.embedding.calibration-interval-ms:3600000}",
            fixedDelayString = "${cat-rescue.embedding.calibration-interval-ms:3600000}"
    )
    public void scheduledRecalibrate() {
        recalibrate();
    }

    public synchronized Status recalibrate() {
        double configured = props.getDistanceThreshold();
        String version = clipEmbeddingClient.embeddingVersion();
        List<ClipThresholdCalibrator.Sample> samples = version == null
                ? List.of()
                : feedbackRepository.findByVolunteerConfirmedIsNotNullAndClipDistanceIsNotNullAndEmbeddingVersion(version)
                        .stream()
                        .filter(ClipThresholdCalibrationService::judgedClipCandidate)
                        .map(r -> new ClipThresholdCalibrator.Sample(r.getClipDistance(), r.getVolunteerConfirmed()))
                        .toList();

        ClipThresholdCalibrator.Result result = ClipThresholdCalibrator.calibrate(
                samples,
                configured,
                props.getCalibrationMinPerClass(),
                props.getCalibrationFloor(),
                props.getCalibrationCeiling()
        );
        double effective = props.isAutoCalibrate() ? result.threshold() : configured;
        Status previous = status;
        status = new Status(configured, effective, props.isAutoCalibrate(), version, Instant.now(), result);
        if (previous == null || Math.abs(previous.effectiveThreshold() - effective) > 1e-6) {
            log.info(
                    "CLIP distance threshold = {} (configured {}, calibrated={}, labels {}+/{}-): {}",
                    String.format(Locale.ROOT, "%.3f", effective),
                    configured,
                    result.calibrated(),
                    result.positives(),
                    result.negatives(),
                    result.reason()
            );
        }
        return status;
    }

    /** The volunteer's verdict is about CLIP's candidate only when CLIP proposed the sighting that was shown. */
    private static boolean judgedClipCandidate(CatMatchFeedbackEntity row) {
        return row.getClipMatchedSightingId() != null
                && Objects.equals(row.getClipMatchedSightingId(), row.getMatchedSightingId());
    }
}
