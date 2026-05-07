package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.routing")
public class RoutingProperties {

    /**
     * Minimum confidence (0–1) to prefer rescue branch when competing.
     */
    private double rescueThreshold = 0.55;

    private double feedingThreshold = 0.45;

    private double tnrThreshold = 0.48;

    public double getRescueThreshold() {
        return rescueThreshold;
    }

    public void setRescueThreshold(double rescueThreshold) {
        this.rescueThreshold = rescueThreshold;
    }

    public double getFeedingThreshold() {
        return feedingThreshold;
    }

    public void setFeedingThreshold(double feedingThreshold) {
        this.feedingThreshold = feedingThreshold;
    }

    public double getTnrThreshold() {
        return tnrThreshold;
    }

    public void setTnrThreshold(double tnrThreshold) {
        this.tnrThreshold = tnrThreshold;
    }
}
