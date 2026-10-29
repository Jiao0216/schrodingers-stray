package com.catrescue.api.service;

import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Generates neighbor-friendly Nextdoor-style text (user still pastes manually; no public auto-post API).
 */
@Service
public class FeedingCopyService {

    public String buildPost(String areaLabel, Double lat, Double lng) {
        String loc = Objects.requireNonNullElse(areaLabel, "San Jose neighborhood");
        String coords = "";
        if (lat != null && lng != null) {
            coords = String.format(" (approx. %.4f, %.4f)", lat, lng);
        }
        return """
                Neighbors — we have community cats nearby that could use coordinated care.

                %s%s

                If you'd like to help: many groups suggest a discreet feeding station with fresh water,
                consistent timing, and cleanup to be good neighbors. Please avoid leaving food overnight
                if it causes pests or complaints — DM me if you want to align on a simple plan.

                This post is from a volunteer using a rescue app — not veterinary advice.

                Thanks for caring for local cats respectfully.
                """.formatted(loc, coords).strip();
    }

    public String suggestedTitle() {
        return "Community cats in the neighborhood — coordinated feeding?";
    }

    /** Simplified Chinese version of {@link #buildPost}. */
    public String buildPostZh(String areaLabel, Double lat, Double lng) {
        String loc = Objects.requireNonNullElse(areaLabel, "圣何塞社区");
        String coords = "";
        if (lat != null && lng != null) {
            coords = String.format("（约 %.4f, %.4f）", lat, lng);
        }
        return """
                邻居们好：附近有几只社区流浪猫，希望大家一起协调照顾。

                %s%s

                如果你愿意帮忙：很多救助组织建议设置一个隐蔽的投喂点，备好清水、固定时间投喂，并及时清理，
                做个好邻居。如果过夜的食物会招来虫害或引起投诉，请不要留到晚上——想一起商量简单方案的话欢迎私信我。

                本帖来自一位使用救助 App 的志愿者，不构成兽医建议。

                感谢大家友善地照顾身边的猫咪。
                """.formatted(loc, coords).strip();
    }

    public String suggestedTitleZh() {
        return "小区里的社区猫——一起协调投喂？";
    }
}
