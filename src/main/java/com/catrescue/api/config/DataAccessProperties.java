package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional bearer-style tokens (sent as HTTP headers) to elevate data-access tier above {@link com.catrescue.api.security.data.DataAccessTier#PUBLIC}.
 */
@ConfigurationProperties(prefix = "cat-rescue.data-access")
public class DataAccessProperties {

    /**
     * Header {@code X-Cat-Rescue-Admin-Data-Token} must match this value for {@code ADMIN} tier.
     */
    private String adminDataToken = "";

    /**
     * Header {@code X-Cat-Rescue-B-Data-Token} must match this value for {@code INSTITUTION} tier (B-end precise data).
     */
    private String bDataToken = "";

    public String getAdminDataToken() {
        return adminDataToken == null ? "" : adminDataToken;
    }

    public void setAdminDataToken(String adminDataToken) {
        this.adminDataToken = adminDataToken;
    }

    public String getBDataToken() {
        return bDataToken == null ? "" : bDataToken;
    }

    public void setBDataToken(String bDataToken) {
        this.bDataToken = bDataToken;
    }
}
