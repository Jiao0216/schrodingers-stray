package com.catrescue.api.config;

import com.catrescue.api.service.client.MultimodalClient;
import com.catrescue.api.service.client.OpenAiMultimodalClient;
import com.catrescue.api.service.client.StubMultimodalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class MultimodalClientConfig {

    private static final Logger log = LoggerFactory.getLogger(MultimodalClientConfig.class);

    @Bean
    public MultimodalClient multimodalClient(
            MultimodalProperties multimodalProperties,
            OpenAiProperties openAiProperties) {

        String mode = multimodalProperties.getProvider() == null
                ? "openai"
                : multimodalProperties.getProvider().trim().toLowerCase();

        String openAiKey = openAiProperties.getApiKey();
        boolean hasOpenAi = openAiKey != null && !openAiKey.isBlank();
        if ("gemini".equals(mode) || "auto".equals(mode)) {
            log.warn("multimodal.provider={} is deprecated in this build; forcing OpenAI-only mode", mode);
        }

        // Default: openai — only real OpenAI; do not silently fall through to Gemini.
        if (hasOpenAi) {
            log.info("Using OpenAI model: {}", openAiProperties.getModel());
            return new OpenAiMultimodalClient(
                    createWebClient(openAiProperties.getBaseUrl()),
                    openAiKey.trim(),
                    openAiProperties.getModel());
        }
        log.warn(
                "multimodal.provider=openai but OPENAI_API_KEY is empty — using StubMultimodalClient");
        return new StubMultimodalClient();
    }

    private static WebClient createWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(120));
        return WebClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
