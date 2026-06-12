package com.catrescue.api.controller;

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

    public InferenceStatusController(
            MultimodalClient multimodalClient,
            MultimodalProperties multimodalProperties,
            OpenAiProperties openAiProperties
    ) {
        this.multimodalClient = multimodalClient;
        this.multimodalProperties = multimodalProperties;
        this.openAiProperties = openAiProperties;
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
        // 不在此接口暴露 baseUrl / 密钥是否配置等细节，避免被前端或第三方爬虫收集。
        if (stub) {
            m.put("hintZh",
                    "当前为本地 Stub 多模态客户端（未连接云端推理）。请在部署环境按仓库 .env.example / 运维文档配置凭证后重启服务；切勿在日志或截图中粘贴密钥。"
            );
            m.put("hintEn",
                    "Stub multimodal client is active (no remote LLM). Configure credentials per deployment docs / .env.example, restart the server, and never paste API keys into logs or screenshots."
            );
        } else {
            m.put("hintZh", "已接入真实 MultimodalClient；若调用失败请查看服务端日志与网络连通性。");
            m.put("hintEn", "Real multimodal client is active; if calls fail, check server logs and network reachability.");
        }
        return m;
    }
}
