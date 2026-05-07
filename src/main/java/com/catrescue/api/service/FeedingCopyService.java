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
}
