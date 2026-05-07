package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.institution")
public class InstitutionProperties {

    /**
     * Shared secret mixed into API token hashes (set via env in production).
     */
    private String tokenPepper = "change-me";

    /**
     * Static admin header value for approving institution applications ({@code X-Cat-Rescue-Institution-Admin}).
     */
    private String adminToken = "";

    public String getTokenPepper() {
        return tokenPepper;
    }

    public void setTokenPepper(String tokenPepper) {
        this.tokenPepper = tokenPepper;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }
}
