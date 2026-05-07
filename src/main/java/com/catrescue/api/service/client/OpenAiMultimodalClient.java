package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI Chat Completions with image input and JSON output.
 */
public class OpenAiMultimodalClient implements MultimodalClient {

    private static final ObjectMapper M = new ObjectMapper();


    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiMultimodalClient(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType) {
        String mime = normalizeMimeType(contentType);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mime + ";base64," + base64;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content",
                                "You are a careful animal welfare vision assistant. Follow the user's JSON schema "
                                        + "exactly; be conservative about illness — flag acute concern only for clearly visible "
                                        + "symptoms. Assess TNR ear-tip (flat surgical crop) before guessing intact vs sterilized."
                        ),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", VisionPrompts.USER_INSTRUCTIONS),
                                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                                )
                        )
                )
        );

        try {
            String json = webClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null || json.isBlank()) {
                throw new OpenAiInvocationException("OpenAI returned empty response body");
            }
            return parseResponse(json);
        } catch (WebClientResponseException e) {
            throw new OpenAiInvocationException(
                    "OpenAI HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            if (e instanceof OpenAiInvocationException oe) {
                throw oe;
            }
            throw new OpenAiInvocationException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    private ModelLabels parseResponse(String json) {
        JsonNode root;
        try {
            root = M.readTree(json);
        } catch (JsonProcessingException e) {
            throw new OpenAiInvocationException("Could not parse OpenAI HTTP JSON", e);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new OpenAiInvocationException("OpenAI returned no choices");
        }

        String content = choices.get(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            String raw = root.toString();
            throw new OpenAiInvocationException(
                    "OpenAI returned empty content. Raw: " + raw.substring(0, Math.min(2000, raw.length()))
            );
        }

        JsonNode out;
        try {
            out = M.readTree(content);
        } catch (JsonProcessingException e) {
            throw new OpenAiInvocationException(
                    "Model output was not valid JSON: " + content.substring(0, Math.min(400, content.length())),
                    e
            );
        }

        ModelLabels parsed = VisionJsonMapper.parse(out);
        return InferenceCalibrationPostProcessor.apply(ModelLabelPostProcessor.adjustForEarTipEvidence(parsed));
    }

    private static String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        String lower = contentType.toLowerCase();
        if (lower.startsWith("image/jpeg") || lower.contains("jpg")) {
            return "image/jpeg";
        }
        if (lower.contains("png")) {
            return "image/png";
        }
        if (lower.contains("webp")) {
            return "image/webp";
        }
        if (lower.contains("heic") || lower.contains("heif")) {
            return "image/heic";
        }
        return "image/jpeg";
    }
}
