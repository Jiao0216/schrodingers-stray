package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local CLIP embedding microservice (embedding-service on :8001).
 */
@ConfigurationProperties(prefix = "cat-rescue.embedding")
public class EmbeddingServiceProperties {

    /** When false, {@link com.catrescue.api.service.client.ClipEmbeddingClient} skips remote calls. */
    private boolean enabled = true;

    /** No trailing slash, e.g. http://127.0.0.1:8001 */
    private String baseUrl = "http://127.0.0.1:8001";

    /** Cosine distance threshold for suspected same-cat (CLIP); fallback when auto-calibration lacks labels. */
    private double distanceThreshold = 0.35;

    /**
     * A CLIP candidate only counts when it was seen within this distance of the new photo. Look-alike cats are
     * common; a match 30 km away is almost always a different cat.
     */
    private double clipMaxDistanceM = 3000;

    /** ...and within this many days of it (cats move and change appearance over time). */
    private int clipMaxAgeDays = 180;

    /** Re-fit the threshold from volunteer confirm/reject feedback (see ClipThresholdCalibrationService). */
    private boolean autoCalibrate = true;

    /** Minimum confirmed AND rejected CLIP matches before the calibrated threshold replaces the configured one. */
    private int calibrationMinPerClass = 5;

    /** Calibrated threshold is clamped to [calibrationFloor, calibrationCeiling]. */
    private double calibrationFloor = 0.10;

    private double calibrationCeiling = 0.60;

    /**
     * Send an auto-detected head crop ({@code POST /crop}) to the vision model as extra ear-tip evidence.
     * Works even when {@link #enabled} is false, as long as embedding-service is running.
     */
    private boolean headCropEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getDistanceThreshold() {
        return distanceThreshold;
    }

    public void setDistanceThreshold(double distanceThreshold) {
        this.distanceThreshold = distanceThreshold;
    }

    public double getClipMaxDistanceM() {
        return clipMaxDistanceM;
    }

    public void setClipMaxDistanceM(double clipMaxDistanceM) {
        this.clipMaxDistanceM = clipMaxDistanceM;
    }

    public int getClipMaxAgeDays() {
        return clipMaxAgeDays;
    }

    public void setClipMaxAgeDays(int clipMaxAgeDays) {
        this.clipMaxAgeDays = clipMaxAgeDays;
    }

    public boolean isAutoCalibrate() {
        return autoCalibrate;
    }

    public void setAutoCalibrate(boolean autoCalibrate) {
        this.autoCalibrate = autoCalibrate;
    }

    public int getCalibrationMinPerClass() {
        return calibrationMinPerClass;
    }

    public void setCalibrationMinPerClass(int calibrationMinPerClass) {
        this.calibrationMinPerClass = calibrationMinPerClass;
    }

    public double getCalibrationFloor() {
        return calibrationFloor;
    }

    public void setCalibrationFloor(double calibrationFloor) {
        this.calibrationFloor = calibrationFloor;
    }

    public double getCalibrationCeiling() {
        return calibrationCeiling;
    }

    public void setCalibrationCeiling(double calibrationCeiling) {
        this.calibrationCeiling = calibrationCeiling;
    }

    public boolean isHeadCropEnabled() {
        return headCropEnabled;
    }

    public void setHeadCropEnabled(boolean headCropEnabled) {
        this.headCropEnabled = headCropEnabled;
    }
}
