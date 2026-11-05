package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dedicated ear-tip (TNR clip) check that runs alongside the main photo analysis and overrides its ear-tip fields.
 */
@ConfigurationProperties(prefix = "cat-rescue.ear-tip")
public class EarTipProperties {

    /** When false, the main analysis' own ear-tip answer is used as before. */
    private boolean enabled = true;

    /** Independent model calls per photo; the verdict needs a two-thirds majority. */
    private int votes = 3;

    /** Model for this check only (e.g. gpt-4o); blank = same model as the main analysis. */
    private String model = "";

    /** A "yes" (already ear-tipped) verdict also needs at least this mean confidence (0–100). */
    private int yesMinConfidence = 75;

    /** Some sampling diversity so the votes are not identical copies. */
    private double temperature = 0.4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getVotes() {
        return votes;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYesMinConfidence() {
        return yesMinConfidence;
    }

    public void setYesMinConfidence(int yesMinConfidence) {
        this.yesMinConfidence = yesMinConfidence;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
