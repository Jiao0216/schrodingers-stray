package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Gemini generateContent with inline image; expects JSON scores in response (responseMimeType application/json).
 */
public class GeminiMultimodalClient implements MultimodalClient {

    private static final ObjectMapper M = new ObjectMapper();


    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GeminiMultimodalClient(WebClient geminiWebClient, String apiKey, String model) {
        this.webClient = geminiWebClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType) {
        String mime = normalizeMimeType(contentType);
        String b64 = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> inline = new HashMap<>();
        inline.put("mime_type", mime);
        inline.put("data", b64);

        Map<String, Object> textPart = Map.of("text", VisionPrompts.USER_INSTRUCTIONS);
        Map<String, Object> imagePart = Map.of("inline_data", inline);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(textPart, imagePart))));
        body.put("generationConfig", generationConfig);

        String path = "/v1beta/models/" + model + ":generateContent";

        try {
            String json = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null || json.isBlank()) {
                throw new GeminiInvocationException("Gemini returned empty response body");
            }
            return parseResponse(json);
        } catch (WebClientResponseException e) {
            throw new GeminiInvocationException(
                    "Gemini HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            if (e instanceof GeminiInvocationException g) {
                throw g;
            }
            throw new GeminiInvocationException("Gemini call failed: " + e.getMessage(), e);
        }
    }

    private ModelLabels parseResponse(String json) {
        JsonNode root;
        try {
            root = M.readTree(json);
        } catch (JsonProcessingException e) {
            throw new GeminiInvocationException("Could not parse Gemini HTTP JSON", e);
        }
        if (root.has("error")) {
            throw new GeminiInvocationException(
                    "Gemini API error: " + root.get("error").toPrettyString()
            );
        }
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new GeminiInvocationException("Gemini returned no candidates");
        }
        String text = candidates.get(0).path("content").path("parts").path(0).path("text").asText("");
        if (text == null || text.isBlank()) {
            String raw = root.toString();
            throw new GeminiInvocationException(
                    "Gemini returned no text (safety block or unsupported image). Raw: "
                            + raw.substring(0, Math.min(2000, raw.length()))
            );
        }
        text = stripJsonFences(text);
        JsonNode out;
        try {
            out = M.readTree(text);
        } catch (JsonProcessingException e) {
            throw new GeminiInvocationException(
                    "Model output was not valid JSON: " + text.substring(0, Math.min(400, text.length())),
                    e
            );
        }

        ModelLabels parsed = VisionJsonMapper.parse(out);
        return InferenceCalibrationPostProcessor.apply(ModelLabelPostProcessor.adjustForEarTipEvidence(parsed));
    }

    private static String stripJsonFences(String text) {
        String t = text.strip();
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
        return t.strip();
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
