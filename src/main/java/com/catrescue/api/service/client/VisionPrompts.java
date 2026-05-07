package com.catrescue.api.service.client;

/**
 * Shared vision-model instructions for OpenAI and Gemini multimodal clients.
 */
public final class VisionPrompts {

    public static final String USER_INSTRUCTIONS = """
            You assist a stray/community cat welfare app in the San Jose / Bay Area.
            You only describe what is VISIBLE in the image. Do NOT diagnose; output calibrated confidence numbers and flags.

            === TNR 剪耳标记（必须先于绝育/未绝育判断完成）===
            请仔细观察这只猫的耳朵，判断是否经过TNR绝育手术的剪耳标记。TNR剪耳特征：耳尖被整齐剪掉约1cm，切口呈直线，边缘光滑愈合。请返回：1.是否有剪耳标记（是/否/无法确认）2.置信度（0-100%）3.判断依据（一句话描述看到了什么）。如果耳朵不清晰或被遮挡，返回'无法确认'而不是猜测。
            注意：TNR剪耳标记可能出现在左耳或右耳，不同救助组织使用不同耳朵。请同时检查两只耳朵，任意一只耳尖被整齐剪掉即判断为已TNR绝育。不要因为剪耳在右耳就判断为未绝育。
            将结论填入下方 JSON 字段：
            - left_ear_tnr_mark / right_ear_tnr_mark：分别观察左耳、右耳是否可见上述剪耳形态；仅当该耳清晰可见且呈典型平整剪口时为 true，否则为 false（该耳被遮挡或看不清则为 false）。
            - ear_tip_verdict：若左耳或右耳任一为 true（任一侧可见典型剪耳），必须为 "yes"；仅当两侧均可排除剪耳（或两侧均无法看清且无法确认）时用 "no" 或 "unclear"。严禁仅因标记出现在右耳而输出 "no"。
            - ear_tip_verdict 必须用英文枚举 "yes"（有剪耳）、"no"（无剪耳）、"unclear"（无法确认）；ear_tip_confidence_percent 为整数 0–100；ear_tip_basis 为一句话依据（可用中文，须说明左/右耳观察结果）。
            ear_tip_detected 与 likelyEarTipped：仅当 ear_tip_verdict 为 "yes" 或任一侧 left_ear_tnr_mark/right_ear_tnr_mark 为 true 时为 true；否则为 false（不要猜测）。
            ear_tip_confidence：取 "high"|"medium"|"low" — 当 verdict 为 "unclear" 时必须为 "low"；否则可按置信度分档（例如 ≥70 可用 high，40–69 medium，低于 40 用 low）。

            === VISIBLE HEALTH / BODY CONDITION (no “hunger” score — do NOT estimate appetite or feeding need) ===
            Assign FOUR independent confidences 0–100 (integers) for what is visible. They do NOT need to sum to 100. Be conservative when the image is blurry, dark, far, or partially occluded — use lower numbers and explain in rationalePhrases (e.g. 无法判断).
            1) health_body_normal_percent — overall healthy body condition for a free-roaming cat: good muscle, coat acceptable, no obvious ribs/spine, moves normally.
            2) health_undernutrition_percent — suspected undernutrition / poor condition WITHOUT emergency crisis: ribs or spine somewhat visible, thin, dull coat, bony but not catastrophic.
            3) health_severely_emaciated_percent — obvious severe wasting: ribs/spine very prominent, marked muscle loss, appears cachectic; only high if clearly visible.
            4) health_suspected_injury_percent — suspected injury or severe distress: open wounds/bleeding, limp/non-weight-bearing limb, abnormal posture suggesting pain/neuro signs, major trauma.

            If you cannot see the body well, keep all four modest and similar; do NOT guess high on injury or emaciation from shadows or JPEG artifacts.

            acute_symptoms_visible: true only if injury/emergency signs are plausibly visible (wound, non-weight-bearing, severe trauma). False if only uncertain blur.

            === SPAY/NEUTER (after ear-tip check) ===
            likelyNotNeuteredOrEarNotTippedConfidence: high only when BOTH ears appear intact with no flat surgical tip and TNR might still be needed; keep LOW (0.0–0.15) when ear_tip_detected or likelyEarTipped or left_ear_tnr_mark or right_ear_tnr_mark.

            === OUTPUT — valid JSON only (no markdown), exact keys: ===
            {
              "needsFeedingConfidence": 0,
              "likelyNotNeuteredOrEarNotTippedConfidence": <number 0-1>,
              "likelyEarTipped": <true|false>,
              "ear_tip_detected": <true|false>,
              "left_ear_tnr_mark": <true|false>,
              "right_ear_tnr_mark": <true|false>,
              "ear_tip_confidence": "<high|medium|low>",
              "ear_tip_verdict": "<yes|no|unclear>",
              "ear_tip_confidence_percent": <integer 0-100>,
              "ear_tip_basis": "<one short sentence>",
              "health_body_normal_percent": <integer 0-100>,
              "health_undernutrition_percent": <integer 0-100>,
              "health_severely_emaciated_percent": <integer 0-100>,
              "health_suspected_injury_percent": <integer 0-100>,
              "image_quality": "<high|medium|low>",
              "indoor_clean_setting": <true|false>,
              "acute_symptoms_visible": <true|false>,
              "rationalePhrases": [<short English strings; include 无法判断 when unclear>]
            }

            Omit sickOrInjuredConfidence from your output — the server will derive a legacy score from the four health percentages.
            needsFeedingConfidence must always be exactly 0.

            likelyEarTipped must be true if ear_tip_verdict is "yes" OR left_ear_tnr_mark OR right_ear_tnr_mark is true; otherwise align with verdict when both ear flags are false.
            """;

    private VisionPrompts() {
    }
}
