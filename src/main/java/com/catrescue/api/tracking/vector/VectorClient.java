package com.catrescue.api.tracking.vector;

import com.catrescue.api.config.ChromaProperties;
import com.catrescue.api.config.EmbeddingServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls embedding-service Chroma endpoints ({@code /chroma/add}, {@code /chroma/query}).
 * Failures are logged and swallowed so OpenAI Vision dedup continues (route B).
 */
@Service
public class VectorClient {

    private static final Logger log = LoggerFactory.getLogger(VectorClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingServiceProperties embeddingProps;
    private final ChromaProperties chromaProps;
    private final WebClient webClient;

    public VectorClient(
            EmbeddingServiceProperties embeddingProps,
            ChromaProperties chromaProps,
            @Qualifier("embeddingServiceWebClient") WebClient webClient
    ) {
        this.embeddingProps = embeddingProps;
        this.chromaProps = chromaProps;
        this.webClient = webClient;
    }

    public boolean isEnabled() {
        return embeddingProps.isEnabled();
    }

    public String collectionName() {
        return chromaProps.getCollection();
    }

    public boolean add(String id, float[] embedding, StrayCatVectorMetadata metadata) {
        if (!embeddingProps.isEnabled()) {
            log.debug("Vector client disabled (embedding service off)");
            return false;
        }
        if (id == null || id.isBlank() || embedding == null || embedding.length == 0 || metadata == null) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.trim());
        body.put("embedding", toDoubleList(embedding));
        body.put("sighting_id", metadata.sightingId());
        body.put("cat_id", metadata.catId());
        body.put("upload_time", metadata.uploadTime() != null ? metadata.uploadTime() : "");
        body.put("location_approx", metadata.locationApprox() != null ? metadata.locationApprox() : "");
        body.put("image_url", metadata.imageUrl() != null ? metadata.imageUrl() : "");

        try {
            String response = webClient.post()
                    .uri("/chroma/add")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseAddResponse(response);
        } catch (WebClientResponseException e) {
            log.warn("Chroma add HTTP {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 200));
            return false;
        } catch (Exception e) {
            log.warn("Chroma add failed: {}", e.getMessage());
            return false;
        }
    }

    public List<VectorMatchResult> queryTopK(float[] embedding, int topK, String excludeId) {
        if (!embeddingProps.isEnabled()) {
            log.debug("Vector client disabled (embedding service off)");
            return List.of();
        }
        if (embedding == null || embedding.length == 0 || topK <= 0) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("embedding", toDoubleList(embedding));
        body.put("top_k", topK);
        if (excludeId != null && !excludeId.isBlank()) {
            body.put("exclude_id", excludeId.trim());
        }

        try {
            String response = webClient.post()
                    .uri("/chroma/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseQueryResponse(response);
        } catch (WebClientResponseException e) {
            log.warn("Chroma query HTTP {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 200));
            return List.of();
        } catch (Exception e) {
            log.warn("Chroma query failed: {}", e.getMessage());
            return List.of();
        }
    }

    static boolean parseAddResponse(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        JsonNode root = MAPPER.readTree(raw);
        return root.path("ok").asBoolean(false);
    }

    static List<VectorMatchResult> parseQueryResponse(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        JsonNode matches = MAPPER.readTree(raw).path("matches");
        if (!matches.isArray()) {
            return List.of();
        }
        List<VectorMatchResult> out = new ArrayList<>();
        for (JsonNode node : matches) {
            long sightingId = node.path("sighting_id").asLong(-1);
            JsonNode catNode = node.path("cat_id");
            Long catId = catNode.isNull() || catNode.isMissingNode() ? null : catNode.asLong();
            out.add(new VectorMatchResult(
                    node.path("id").asText(""),
                    sightingId,
                    catId,
                    node.path("distance").asDouble(),
                    node.path("upload_time").asText(""),
                    node.path("location_approx").asText(""),
                    node.path("image_url").asText("")
            ));
        }
        return out;
    }

    private static List<Double> toDoubleList(float[] embedding) {
        List<Double> list = new ArrayList<>(embedding.length);
        for (float v : embedding) {
            list.add((double) v);
        }
        return list;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
