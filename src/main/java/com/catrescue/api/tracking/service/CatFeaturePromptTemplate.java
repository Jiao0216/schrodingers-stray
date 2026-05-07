package com.catrescue.api.tracking.service;

public final class CatFeaturePromptTemplate {

    private CatFeaturePromptTemplate() {
    }

    public static final String GPT4O_PROMPT = """
            You are an assistant for a stray cat tracking platform.
            Analyze the cat image and output ONLY strict JSON with this schema:
            {
              "coatColor": "string",
              "patternType": "string",
              "bodySize": "small|medium|large|unknown",
              "specialFeatures": ["string", "..."],
              "earTipped": true|false,
              "earTippedConfidence": 0.0-1.0,
              "extractionConfidence": 0.0-1.0,
              "summary": "short plain-English summary"
            }

            Rules:
            - Describe only what is actually visible in the image; do not infer unseen facts.
            - If a feature is unclear, use "unknown" for coatColor/patternType/bodySize.
            - Keep summary in plain English; if unclear, say "unknown".
            - Never fabricate cats or attributes that are not visible.
            - If only one cat is visible, this JSON must describe only that one cat.
            - Keep cat count consistent with what is visible (do not imply extra cats).
            - Focus on dedup-relevant traits: coat color, tabby/solid/calico pattern, body size, eye traits, scars, missing fur, tail shape.
            - If ear tip notch/cut is clearly visible (TNR marker), set earTipped=true.
            - If ear visibility is poor, set earTipped=false and low confidence.
            - Do not add markdown fences and do not include extra keys.
            """;
}
