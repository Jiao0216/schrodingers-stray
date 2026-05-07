package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which backend to use for image assessment ({@link com.catrescue.api.service.client.MultimodalClient}).
 */
@ConfigurationProperties(prefix = "cat-rescue.multimodal")
public class MultimodalProperties {

    /**
     * OpenAI-only runtime: {@code openai} is the supported mode.
     * Legacy values ({@code gemini}, {@code auto}) are accepted but coerced to OpenAI.
     */
    private String provider = "openai";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
