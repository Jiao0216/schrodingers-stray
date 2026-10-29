package com.catrescue.api.service;

import com.catrescue.api.dto.AssessmentActionGuidanceDto;
import com.catrescue.api.tracking.dto.FeedingSpotResponse;
import com.catrescue.api.tracking.dto.ShelterInfoResponse;
import com.catrescue.api.tracking.service.FeedingSpotService;
import com.catrescue.api.tracking.service.ShelterInfoService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AssessmentActionGuidanceService {

    private static final String BOWL_SEARCH_URL =
            "https://www.amazon.com/s?k=outdoor+cat+bowl+community+feeding";

    private final ShelterInfoService shelterInfoService;
    private final FeedingSpotService feedingSpotService;

    public AssessmentActionGuidanceService(
            ShelterInfoService shelterInfoService,
            FeedingSpotService feedingSpotService
    ) {
        this.shelterInfoService = shelterInfoService;
        this.feedingSpotService = feedingSpotService;
    }

    /**
     * @param earClip       effective ear-tip status: "yes" | "no" | "unclear"
     * @param earClipSource "user" (volunteer answered) or "ai" (left to the AI ear-tip check)
     */
    public AssessmentActionGuidanceDto build(String earClip, String earClipSource, Double latitude, Double longitude) {
        String clip = normalizeEarClip(earClip);
        boolean fromAi = "ai".equals(earClipSource);
        // Unless the cat is (said to be) ear-tipped it may still need TNR — show resources for "unclear" too.
        boolean showTnr = !"yes".equals(clip);
        List<ShelterInfoResponse> shelters = List.of();
        if (showTnr && latitude != null && longitude != null) {
            shelters = shelterInfoService.nearest(latitude, longitude, 5, "TNR");
        }

        List<FeedingSpotResponse> spotsNearby = List.of();
        if (latitude != null && longitude != null) {
            spotsNearby = feedingSpotService.nearestActive(
                    latitude,
                    longitude,
                    FeedingSpotService.DEFAULT_NEARBY_RADIUS_M,
                    8
            );
        }
        boolean showFeedingPrompt = spotsNearby.isEmpty();

        List<String> lines = new ArrayList<>();
        if ("no".equals(clip)) {
            lines.add(fromAi
                    ? "AI 判断未剪耳：建议了解附近 TNR/绝育资源，安全诱捕后再送机构。"
                    : "您标记为未剪耳：建议了解附近 TNR/绝育资源，安全诱捕后再送机构。");
        } else if ("yes".equals(clip)) {
            lines.add(fromAi
                    ? "AI 判断已剪耳：通常表示已参与 TNR，可持续观察与社区喂养。"
                    : "您标记为已剪耳：通常表示已参与 TNR，可持续观察与社区喂养。");
        } else {
            lines.add("AI 无法确认是否剪耳：请现场近距离查看耳朵；以下附近 TNR 资源供参考。");
        }
        if (showFeedingPrompt) {
            lines.add("附近暂无登记投喂点：可用旧碗/一次性餐盒先喂，欢迎拍照登记一个新投喂点。");
        } else {
            lines.add("附近已有社区投喂点，欢迎打卡「我喂了」方便其他志愿者。");
        }

        return new AssessmentActionGuidanceDto(
                clip,
                fromAi ? "ai" : "user",
                showTnr,
                shelters,
                showFeedingPrompt,
                spotsNearby,
                BOWL_SEARCH_URL,
                lines
        );
    }

    private static String normalizeEarClip(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "yes", "y", "true", "1" -> "yes";
            case "no", "n", "false", "0" -> "no";
            default -> "unclear";
        };
    }
}
