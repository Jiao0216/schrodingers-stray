package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.openai")
public class OpenAiProperties {

    /** OpenAI API key; if set, OpenAI client is preferred over Gemini. */
    private String apiKey;

    /** Vision-capable model. */
    private String model = "gpt-4o-mini";

    private String baseUrl = "https://api.openai.com";

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
