package com.catrescue.api.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Listens on {@code CAT_RESCUE_HTTP_PORT}, else {@code PORT} (Railway / Render / Fly), else 8090.
 * Spring Boot maps {@code SERVER_PORT} to {@code server.port}; avoid conflicting env when possible.
 */
@Configuration
public class HttpPortConfiguration {

    private static final int DEFAULT_PORT = 8090;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> catRescueHttpPort() {
        int listenPort = resolvePort();
        return factory -> factory.setPort(listenPort);
    }

    private static int resolvePort() {
        Integer fromCat = parseEnvPort("CAT_RESCUE_HTTP_PORT");
        if (fromCat != null) {
            return fromCat;
        }
        Integer fromPlatform = parseEnvPort("PORT");
        if (fromPlatform != null) {
            return fromPlatform;
        }
        return DEFAULT_PORT;
    }

    private static Integer parseEnvPort(String name) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
