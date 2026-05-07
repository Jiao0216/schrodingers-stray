package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.site")
public class SiteProperties {

    /**
     * Public site URL for share links (e.g. https://cats.example.com). No trailing slash.
     * When empty, clients should build URLs from {@code window.location.origin}.
     */
    private String publicBaseUrl = "";

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
