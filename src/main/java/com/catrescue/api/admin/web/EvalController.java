package com.catrescue.api.admin.web;

import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.service.client.ClipEmbeddingClient;
import com.catrescue.api.service.client.EarTipJudgement;
import com.catrescue.api.service.client.MultimodalClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Accuracy evaluation helpers (used by {@code eval/run_eval.py}). Nothing is saved: these run the ear-tip check on
 * one photo with explicit settings so configurations can be compared on a labelled photo set.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/admin/eval")
public class EvalController {

    private final MultimodalClient multimodalClient;
    private final ClipEmbeddingClient clipEmbeddingClient;

    public EvalController(MultimodalClient multimodalClient, ClipEmbeddingClient clipEmbeddingClient) {
        this.multimodalClient = multimodalClient;
        this.clipEmbeddingClient = clipEmbeddingClient;
    }

    /**
     * @param mode     {@code judge} (dedicated voting check, the production path) or {@code main} (the ear-tip answer
     *                 of the main analysis prompt alone, i.e. the old behaviour)
     * @param votes    judge only: votes per photo (default: configured value)
     * @param model    judge only: model override, e.g. {@code gpt-4o}
     * @param headCrop send the auto-detected head close-up as extra evidence
     */
    @PostMapping(value = "/ear-tip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> earTip(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "mode", defaultValue = "judge") String mode,
            @RequestParam(value = "votes", required = false) Integer votes,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "headCrop", defaultValue = "true") boolean headCrop
    ) throws IOException {
        byte[] bytes = image.getBytes();
        String contentType = image.getContentType();
        byte[] crop = headCrop ? clipEmbeddingClient.cropHead(bytes, contentType).orElse(null) : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("headCropUsed", crop != null);
        if ("main".equals(mode)) {
            ModelLabels m = multimodalClient.analyzeImageBytes(bytes, contentType, crop);
            out.put("note", "main mode also runs the dedicated check when cat-rescue.ear-tip.enabled=true; "
                    + "disable it (CAT_RESCUE_EAR_TIP_ENABLED=false) to measure the main prompt alone");
            out.put("verdict", m.earTipVerdict() == null || m.earTipVerdict().isEmpty()
                    ? (m.likelyEarTipped() ? "yes" : "no") : m.earTipVerdict());
            out.put("confidencePercent", m.earTipConfidencePercent());
            out.put("basisZh", m.earTipBasis());
            out.put("basisEn", m.earTipBasisEn());
            return out;
        }
        Optional<EarTipJudgement> j = multimodalClient.judgeEarTip(bytes, contentType, crop, votes, model);
        if (j.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ear-tip check unavailable (stub client, or every model call failed)");
        }
        EarTipJudgement r = j.get();
        out.put("verdict", r.verdict());
        out.put("confidencePercent", r.confidencePercent());
        out.put("votesYes", r.votesYes());
        out.put("votesNo", r.votesNo());
        out.put("votesUnclear", r.votesUnclear());
        out.put("model", r.model());
        out.put("basisZh", r.basisZh());
        out.put("basisEn", r.basisEn());
        return out;
    }
}
