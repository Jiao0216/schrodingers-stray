package com.catrescue.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({EmbeddingServiceProperties.class, ChromaProperties.class})
public class EmbeddingServiceClientConfig {

    @Bean(name = "embeddingServiceWebClient")
    public WebClient embeddingServiceWebClient(EmbeddingServiceProperties props) {
        String base = props.getBaseUrl() == null ? "" : props.getBaseUrl().replaceAll("/+$", "");
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(120));
        return WebClient.builder()
                .baseUrl(base.isEmpty() ? "http://127.0.0.1:8001" : base)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
