package com.catrescue.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        RoutingProperties.class,
        GeminiProperties.class,
        OpenAiProperties.class,
        MultimodalProperties.class,
        AssessmentPersistenceProperties.class,
        ShareAccessProperties.class,
        InstitutionProperties.class,
        SiteProperties.class
})
public class AppConfig {
}
