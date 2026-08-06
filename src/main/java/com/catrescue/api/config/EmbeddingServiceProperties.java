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

    /** Cosine distance threshold for suspected same-cat (used in later dedup steps). */
    private double distanceThreshold = 0.199;

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
}
