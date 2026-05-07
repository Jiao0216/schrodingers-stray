package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.assessment")
public class AssessmentPersistenceProperties {

    /** Store uploaded assessment images in the database (MEDIUMBLOB / LOB). */
    private boolean persistImages = true;

    public boolean isPersistImages() {
        return persistImages;
    }

    public void setPersistImages(boolean persistImages) {
        this.persistImages = persistImages;
    }
}
