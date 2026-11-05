package com.catrescue.api.service.client;

import com.catrescue.api.config.EarTipProperties;
import com.catrescue.api.domain.ModelLabels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls OpenAI Chat Completions with image input and JSON output.
 */
public class OpenAiMultimodalClient implements MultimodalClient {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OpenAiMultimodalClient.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final EarTipProperties earTip;
    /** Ear-tip votes run in parallel with the main analysis (blocking WebClient calls, so not the common pool). */
    private final ExecutorService earTipExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ear-tip-vote");
        t.setDaemon(true);
        return t;
    });

    public OpenAiMultimodalClient(WebClient webClient, String apiKey, String model, EarTipProperties earTip) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
        this.earTip = earTip != null ? earTip : new EarTipProperties();
    }

    @Override
    public ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType) {
        return analyzeImageBytes(imageBytes, contentType, null);
    }

    @Override
    public ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType, byte[] headCropJpeg) {
        CompletableFuture<Optional<EarTipJudgement>> earTipFuture = earTip.isEnabled()
                ? CompletableFuture.supplyAsync(
                        () -> judgeEarTip(imageBytes, contentType, headCropJpeg, earTip.getVotes(), earTip.getModel()),
                        earTipExecutor)
                : CompletableFuture.completedFuture(Optional.empty());

        ModelLabels parsed = analyzeMain(imageBytes, contentType, headCropJpeg);
        Optional<EarTipJudgement> judgement = earTipFuture.join();
        if (judgement.isPresent()) {
            parsed = judgement.get().applyTo(parsed);
        }
        return InferenceCalibrationPostProcessor.apply(ModelLabelPostProcessor.adjustForEarTipEvidence(parsed));
    }

    /** Main analysis only (health, ear-tip fields from the big prompt), before post-processing. */
    ModelLabels analyzeMain(byte[] imageBytes, String contentType, byte[] headCropJpeg) {
        String dataUrl = toDataUrl(imageBytes, contentType);

        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "text", "text", VisionPrompts.USER_INSTRUCTIONS));
        userContent.add(highDetailImage(dataUrl));
        if (headCropJpeg != null && headCropJpeg.length > 0) {
            userContent.add(Map.of("type", "text", "text", VisionPrompts.HEAD_CROP_NOTE));
            userContent.add(highDetailImage(
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(headCropJpeg)
            ));
        }

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
                                "content", userContent
                        )
                )
        );
        return VisionJsonMapper.parse(callJson(body));
    }

    @Override
    public Optional<EarTipJudgement> judgeEarTip(
            byte[] imageBytes, String contentType, byte[] headCropJpeg, Integer votes, String modelOverride
    ) {
        int n = votes != null && votes > 0 ? Math.min(votes, 7) : earTip.getVotes();
        String voteModel = modelOverride != null && !modelOverride.isBlank() ? modelOverride.trim() : model;
        boolean withCrop = headCropJpeg != null && headCropJpeg.length > 0;

        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "text", "text", EarTipPrompts.user(withCrop)));
        if (withCrop) {
            userContent.add(highDetailImage("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(headCropJpeg)));
        }
        userContent.add(highDetailImage(toDataUrl(imageBytes, contentType)));
        Map<String, Object> body = Map.of(
                "model", voteModel,
                "temperature", earTip.getTemperature(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", EarTipPrompts.SYSTEM),
                        Map.of("role", "user", "content", userContent)
                )
        );

        List<CompletableFuture<Optional<EarTipJudgement.Vote>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return Optional.of(parseVote(callJson(body)));
                } catch (Exception e) {
                    // A failed call is dropped rather than counted as "unclear".
                    log.warn("Ear-tip vote failed: {}", e.getMessage());
                    return Optional.<EarTipJudgement.Vote>empty();
                }
            }, earTipExecutor));
        }
        List<EarTipJudgement.Vote> ok = futures.stream().map(CompletableFuture::join).flatMap(Optional::stream).toList();
        if (ok.isEmpty()) {
            log.warn("All {} ear-tip votes failed; keeping the main analysis' ear-tip answer", n);
            return Optional.empty();
        }
        EarTipJudgement j = EarTipJudgement.aggregate(ok, earTip.getYesMinConfidence(), voteModel);
        log.info("Ear-tip verdict={} conf={} votes yes/no/unclear={}/{}/{} model={} crop={}",
                j.verdict(), j.confidencePercent(), j.votesYes(), j.votesNo(), j.votesUnclear(), voteModel, withCrop);
        return Optional.of(j);
    }

    /**
     * One vote. The verdict is re-derived from the per-ear answers; if the model's own verdict contradicts them the
     * vote becomes "unclear" (an internally inconsistent answer is not evidence either way).
     */
    static EarTipJudgement.Vote parseVote(JsonNode out) {
        String left = out.path("left_ear").asText("").trim().toLowerCase(Locale.ROOT);
        String right = out.path("right_ear").asText("").trim().toLowerCase(Locale.ROOT);
        String stated = out.path("verdict").asText("").trim().toLowerCase(Locale.ROOT);
        String derived;
        if ("tipped".equals(left) || "tipped".equals(right)) {
            derived = "yes";
        } else if ("intact".equals(left) && "intact".equals(right)) {
            derived = "no";
        } else {
            derived = "unclear";
        }
        String verdict = derived.equals(stated) ? derived : "unclear";
        int conf = out.path("confidence_percent").asInt(0);
        return new EarTipJudgement.Vote(
                verdict,
                Math.max(0, Math.min(100, conf)),
                out.path("basis_zh").asText("").trim(),
                out.path("basis_en").asText("").trim()
        );
    }

    /** POSTs a chat completion and returns the model's JSON message content. */
    private JsonNode callJson(Map<String, Object> body) {
        String json;
        try {
            json = webClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new OpenAiInvocationException(
                    "OpenAI HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new OpenAiInvocationException("OpenAI call failed: " + e.getMessage(), e);
        }
        if (json == null || json.isBlank()) {
            throw new OpenAiInvocationException("OpenAI returned empty response body");
        }
        return parseContent(json);
    }

    /** detail=high keeps small regions (ear tips) from being lost to the default downscale. */
    private static Map<String, Object> highDetailImage(String url) {
        return Map.of("type", "image_url", "image_url", Map.of("url", url, "detail", "high"));
    }

    private static String toDataUrl(byte[] imageBytes, String contentType) {
        return "data:" + normalizeMimeType(contentType) + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    private static JsonNode parseContent(String json) {
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

        try {
            return M.readTree(content);
        } catch (JsonProcessingException e) {
            throw new OpenAiInvocationException(
                    "Model output was not valid JSON: " + content.substring(0, Math.min(400, content.length())),
                    e
            );
        }
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
