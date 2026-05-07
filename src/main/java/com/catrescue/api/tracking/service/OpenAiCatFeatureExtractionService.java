package com.catrescue.api.tracking.service;

import com.catrescue.api.config.OpenAiProperties;
import com.catrescue.api.tracking.dto.CatFeatureVector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OpenAiCatFeatureExtractionService implements CatFeatureExtractionService {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OpenAiCatFeatureExtractionService.class);
    private final OpenAiProperties openAiProperties;
    private final WebClient webClient;
    private final boolean mockFeatureExtraction;

    public OpenAiCatFeatureExtractionService(
            OpenAiProperties openAiProperties,
            @Value("${cat-rescue.tracking.mock-feature-extraction:false}") boolean mockFeatureExtraction
    ) {
        this.openAiProperties = openAiProperties;
        this.mockFeatureExtraction = mockFeatureExtraction;
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(120));
        this.webClient = WebClient.builder()
                .baseUrl(openAiProperties.getBaseUrl().replaceAll("/$", ""))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public CatFeatureVector extractFromImage(String imageUrlOrDataUrl) {
        String apiKey = openAiProperties.getApiKey();
        if (mockFeatureExtraction) {
            log.info("Tracking feature extraction uses mock mode (cat-rescue.tracking.mock-feature-extraction=true)");
            return mockFeatureVector(imageUrlOrDataUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY is missing; fallback to mock tracking feature extraction");
            return mockFeatureVector(imageUrlOrDataUrl);
        }

        Map<String, Object> body = Map.of(
                "model", resolveModel(),
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role",
                                "system",
                                "content",
                                "You extract structured cat appearance features with strict visual grounding. "
                                        + "Only describe what is visible, never guess, and use 'unknown' when unclear."
                        ),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", CatFeaturePromptTemplate.GPT4O_PROMPT),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrlOrDataUrl))
                        ))
                )
        );

        try {
            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("OpenAI feature extraction returned empty response");
            }
            return parse(response);
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("OpenAI feature extraction HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI feature extraction failed: " + e.getMessage(), e);
        }
    }

    private CatFeatureVector parse(String rawResponse) throws Exception {
        JsonNode root = M.readTree(rawResponse);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no choices for feature extraction");
        }
        String content = choices.get(0).path("message").path("content").asText("");
        JsonNode out = M.readTree(stripJsonFences(content));

        List<String> features = new ArrayList<>();
        JsonNode arr = out.path("specialFeatures");
        if (arr.isArray()) {
            arr.forEach(n -> {
                if (n.isTextual()) {
                    features.add(n.asText());
                }
            });
        }

        return new CatFeatureVector(
                sanitizeCategory(out.path("coatColor").asText("unknown")),
                sanitizeCategory(out.path("patternType").asText("unknown")),
                sanitizeBodySize(out.path("bodySize").asText("unknown")),
                List.copyOf(features),
                out.path("earTipped").asBoolean(false),
                clamp01(out.path("earTippedConfidence").asDouble(0.0)),
                clamp01(out.path("extractionConfidence").asDouble(0.5)),
                sanitizeSummary(out.path("summary").asText(""))
        );
    }

    private String resolveModel() {
        String model = openAiProperties.getModel();
        if (model == null || model.isBlank()) {
            return "gpt-4o";
        }
        return model;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String sanitizeCategory(String in) {
        String s = in == null ? "" : in.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank() || s.contains("无法判断") || s.contains("cannot") || s.contains("unknown")) {
            return "unknown";
        }
        // Keep database-safe ASCII token-ish content only.
        s = s.replaceAll("[^a-z0-9 _-]", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s.isBlank() ? "unknown" : s;
    }

    private static String sanitizeBodySize(String in) {
        String s = sanitizeCategory(in);
        return switch (s) {
            case "small", "medium", "large" -> s;
            default -> "unknown";
        };
    }

    private static String sanitizeSummary(String in) {
        String s = in == null ? "" : in.trim();
        if (s.isBlank() || s.contains("无法判断")) {
            return "unknown";
        }
        return s;
    }

    private static String stripJsonFences(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n');
            if (start > 0) {
                t = t.substring(start + 1);
            }
            int end = t.lastIndexOf("```");
            if (end > 0) {
                t = t.substring(0, end);
            }
        }
        return t.trim();
    }

    private static CatFeatureVector mockFeatureVector(String imageRef) {
        String seed = imageRef == null ? "" : imageRef;
        int h = Math.abs(seed.hashCode());
        String[] coats = {"gray", "orange", "black", "white", "brown", "calico", "unknown"};
        String[] patterns = {"tabby", "solid", "bicolor", "tortoiseshell", "unknown"};
        String[] sizes = {"small", "medium", "large", "unknown"};
        String coat = coats[h % coats.length];
        String pattern = patterns[(h / 7) % patterns.length];
        String size = sizes[(h / 17) % sizes.length];
        boolean earTipped = ((h / 31) % 10) < 2; // keep conservative: ~20%
        double earConf = earTipped ? 0.72 : 0.18;
        List<String> features = List.of(
                "mock-mode",
                "seed-" + Integer.toHexString(h % 65535)
        );
        return new CatFeatureVector(
                sanitizeCategory(coat),
                sanitizeCategory(pattern),
                sanitizeBodySize(size),
                features,
                earTipped,
                earConf,
                0.35,
                "mock feature extraction (no external multimodal call)"
        );
    }
}
