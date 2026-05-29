package com.catrescue.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地 / 内网 Python FastAPI（cat-agent）联调用配置。
 */
@ConfigurationProperties(prefix = "cat-rescue.python-agent")
public class PythonAgentProperties {

    /** 设为 false 可关闭代理（前端仍会请求，会收到 503）。 */
    private boolean enabled = true;

    /** 无末尾斜杠，例如 http://127.0.0.1:8000 */
    private String baseUrl = "http://127.0.0.1:8000";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
