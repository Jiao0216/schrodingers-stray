package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chroma collection settings (vectors stored via embedding-service Python sidecar).
 */
@ConfigurationProperties(prefix = "cat-rescue.chroma")
public class ChromaProperties {

    /** Collection name on the embedding-service (default {@code stray_cats}). */
    private String collection = "stray_cats";

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
