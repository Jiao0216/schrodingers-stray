package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.gemini")
public class GeminiProperties {

    /** Google AI Studio / Gemini API key; leave empty to use StubMultimodalClient */
    private String apiKey;

    /** Bound from cat-rescue.gemini.model (default 1.5-flash — 2.0 often hits free-tier limit:0) */
    private String model = "gemini-1.5-flash";

    /** Override only if Google changes REST host */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
