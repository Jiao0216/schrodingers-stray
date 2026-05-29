package com.catrescue.api.agent.web;

import com.catrescue.api.config.PythonAgentProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 将浏览器同源请求转发到 Python FastAPI（uvicorn api:app），避免跨域并统一联调入口。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class PythonAgentProxyController {

    private final PythonAgentProperties props;
    private final WebClient pythonAgentWebClient;

    public PythonAgentProxyController(PythonAgentProperties props, WebClient pythonAgentWebClient) {
        this.props = props;
        this.pythonAgentWebClient = pythonAgentWebClient;
    }

    @PostMapping(value = "/analyze/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> analyzeComplete(
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "location", required = false, defaultValue = "") String location,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "ear_clip", required = false, defaultValue = "unknown") String earClip,
            @RequestParam(value = "include_behavior", required = false, defaultValue = "true") boolean includeBehavior) {
        if (!props.isEnabled()) {
            byte[] body = "{\"detail\":\"Python agent proxy disabled (cat-rescue.python-agent.enabled=false)\"}"
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", image.getResource());
        builder.part("location", location != null ? location : "");
        if (lat != null) {
            builder.part("lat", lat.toString());
        }
        if (lng != null) {
            builder.part("lng", lng.toString());
        }
        builder.part("ear_clip", earClip != null ? earClip : "unknown");
        builder.part("include_behavior", Boolean.toString(includeBehavior));

        try {
            return pythonAgentWebClient
                    .post()
                    .uri("/v1/analyze/complete")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .toEntity(byte[].class)
                    .block(java.time.Duration.ofMinutes(5));
        } catch (WebClientResponseException e) {
            MediaType ct = e.getHeaders().getContentType();
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsByteArray());
        } catch (WebClientRequestException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "upstream unreachable";
            String json = "{\"detail\":\"Python agent unreachable: " + escapeJson(msg) + "\"}";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            String json = "{\"detail\":\"Python agent proxy error: " + escapeJson(e.getMessage()) + "\"}";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
