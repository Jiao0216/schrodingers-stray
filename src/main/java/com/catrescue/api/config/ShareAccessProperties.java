package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cat-rescue.share")
public class ShareAccessProperties {

    /**
     * Enable lightweight password gate for temporary external testing.
     */
    private boolean enabled = false;

    /**
     * Shared test password. Keep it in env var, never commit real value.
     */
    private String password = "";

    /**
     * Cookie name used after successful password check.
     */
    private String cookieName = "cat_rescue_share_auth";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
}
