package com.catrescue.api.controller;

import com.catrescue.api.config.GeminiProperties;
import com.catrescue.api.config.MultimodalProperties;
import com.catrescue.api.config.OpenAiProperties;
import com.catrescue.api.service.client.MultimodalClient;
import com.catrescue.api.service.client.StubMultimodalClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only diagnostics: whether the JVM is using {@link StubMultimodalClient} (no API key) or a real client.
 */
@RestController
@RequestMapping("/api/v1")
public class InferenceStatusController {

    private final MultimodalClient multimodalClient;
    private final MultimodalProperties multimodalProperties;
    private final OpenAiProperties openAiProperties;
    private final GeminiProperties geminiProperties;

    public InferenceStatusController(
            MultimodalClient multimodalClient,
            MultimodalProperties multimodalProperties,
            OpenAiProperties openAiProperties,
            GeminiProperties geminiProperties
    ) {
        this.multimodalClient = multimodalClient;
        this.multimodalProperties = multimodalProperties;
        this.openAiProperties = openAiProperties;
        this.geminiProperties = geminiProperties;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/inference-status")
    public Map<String, Object> inferenceStatus() {
        boolean stub = multimodalClient instanceof StubMultimodalClient;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usingRealModel", !stub);
        m.put("usingStub", stub);
        m.put("multimodalProvider", multimodalProperties.getProvider());
        m.put("openaiModel", openAiProperties.getModel());
        m.put("openaiBaseUrl", openAiProperties.getBaseUrl());
        m.put("openaiKeyPresent", present(openAiProperties.getApiKey()));
        m.put("geminiKeyPresent", present(geminiProperties.getApiKey()));
        if (stub) {
            m.put("hintZh",
                    "当前未加载 OPENAI_API_KEY（或 provider 与密钥不匹配），正在使用本地 Stub，不是真模型。"
                            + " 请在运行后端的终端执行: export OPENAI_API_KEY=sk-... 再 mvn spring-boot:run；"
                            + " 若用 IDE 运行，在 Run Configuration → Environment 里添加 OPENAI_API_KEY。"
            );
            m.put("hintEn",
                    "OPENAI_API_KEY is missing or mismatched with MULTIMODAL_PROVIDER — StubMultimodalClient is active (not real OpenAI). "
                            + "Export OPENAI_API_KEY in the shell before mvn, or set env vars in your IDE run configuration."
            );
        } else {
            m.put("hintZh", "已使用真实 MultimodalClient；若仍失败请查看终端 ERROR 与 OPENAI_BASE_URL 网络是否可达。");
            m.put("hintEn", "Real multimodal client is wired; if calls fail, check server logs and OPENAI_BASE_URL reachability.");
        }
        return m;
    }

    private static boolean present(String k) {
        return k != null && !k.isBlank();
    }
}
