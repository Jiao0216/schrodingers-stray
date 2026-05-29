package com.catrescue.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(PythonAgentProperties.class)
public class PythonAgentClientConfig {

    @Bean
    public WebClient pythonAgentWebClient(PythonAgentProperties props) {
        String base = props.getBaseUrl() == null ? "" : props.getBaseUrl().replaceAll("/+$", "");
        return WebClient.builder().baseUrl(base.isEmpty() ? "http://127.0.0.1:8000" : base).build();
    }
}
