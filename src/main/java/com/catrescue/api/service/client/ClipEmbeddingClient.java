package com.catrescue.api.service.client;

import com.catrescue.api.config.EmbeddingServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Locale;
import java.util.Optional;

/**
 * Calls the local CLIP embedding microservice ({@code POST /embed}).
 * Failures return empty so OpenAI Vision dedup can continue (route B parallel mode).
 */
@Service
public class ClipEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(ClipEmbeddingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingServiceProperties props;
    private final WebClient webClient;
    private volatile String embeddingVersion;

    public ClipEmbeddingClient(
            EmbeddingServiceProperties props,
            @Qualifier("embeddingServiceWebClient") WebClient webClient
    ) {
        this.props = props;
        this.webClient = webClient;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** Pipeline version reported by the last successful {@code /embed} call; null until one succeeds. */
    public String embeddingVersion() {
        return embeddingVersion;
    }

    public Optional<float[]> embed(MultipartFile file) {
        if (!props.isEnabled()) {
            log.debug("CLIP embedding service disabled");
            return Optional.empty();
        }
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            String contentType = file.getContentType() != null ? file.getContentType() : guessMimeFromName(filename);
            return embedBytes(bytes, filename, contentType);
        } catch (Exception e) {
            log.warn("CLIP embed from MultipartFile failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<float[]> embedBytes(byte[] bytes, String filename, String contentType) {
        if (!props.isEnabled()) {
            log.debug("CLIP embedding service disabled");
            return Optional.empty();
        }
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            String response = webClient.post()
                    .uri("/embed")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(imagePart(bytes, filename, contentType)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            rememberEmbeddingVersion(response);
            return parseEmbeddingResponse(response);
        } catch (WebClientResponseException e) {
            log.warn(
                    "CLIP embed HTTP {}: {}",
                    e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString(), 200)
            );
            return Optional.empty();
        } catch (Exception e) {
            log.warn("CLIP embed failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Padded, upscaled JPEG crop of the detected cat head ({@code POST /crop?target=head}) for ear-tip checks.
     * Empty when the service is off, no head is detected, or the call fails — callers fall back to the full image.
     */
    public Optional<byte[]> cropHead(byte[] bytes, String contentType) {
        // Independent of props.isEnabled(): ear-tip crops are useful even while CLIP dedup is paused.
        if (!props.isHeadCropEnabled() || bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            byte[] jpeg = webClient.post()
                    .uri("/crop?target=head")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(imagePart(bytes, "upload.jpg", contentType)))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            return jpeg == null || jpeg.length == 0 ? Optional.empty() : Optional.of(jpeg);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("No cat head detected for ear-tip crop");
            } else {
                log.warn("Head crop HTTP {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 200));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Head crop failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static MultiValueMap<String, HttpEntity<?>> imagePart(byte[] bytes, String filename, String contentType) {
        String safeName = filename == null || filename.isBlank() ? "upload.jpg" : filename;
        String safeType = contentType == null || contentType.isBlank() ? guessMimeFromName(safeName) : contentType;
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        }).contentType(MediaType.parseMediaType(safeType));
        return builder.build();
    }

    private void rememberEmbeddingVersion(String raw) {
        try {
            String v = raw == null ? "" : MAPPER.readTree(raw).path("embedding_version").asText("");
            if (!v.isBlank()) {
                embeddingVersion = v;
            }
        } catch (Exception ignored) {
            // parseEmbeddingResponse reports malformed bodies.
        }
    }

    static Optional<float[]> parseEmbeddingResponse(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = MAPPER.readTree(raw);
        JsonNode arr = root.path("embedding");
        if (!arr.isArray() || arr.isEmpty()) {
            return Optional.empty();
        }
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = (float) arr.get(i).asDouble();
        }
        return Optional.of(out);
    }

    private static String guessMimeFromName(String name) {
        if (name == null) {
            return "image/jpeg";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
