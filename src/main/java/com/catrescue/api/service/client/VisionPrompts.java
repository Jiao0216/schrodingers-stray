package com.catrescue.api.service.client;

/**
 * Shared vision-model instructions for OpenAI and Gemini multimodal clients.
 */
public final class VisionPrompts {

    public static final String USER_INSTRUCTIONS = """
            You assist a stray/community cat welfare app in the San Jose / Bay Area.
            You only describe what is VISIBLE in the image. Do NOT diagnose; output calibrated confidence numbers and flags.

            === TNR EAR TIP (decide before intact vs sterilized) ===
            A TNR ear tip: the top ~1 cm (about the top quarter) of ONE ear removed with a straight, flat cut, leaving a \
            squared-off edge. It can be on either ear. NOT ear tips: naturally pointed/rounded tips, fur tufts, folded or \
            turned ears, ears partly hidden, blur or low resolution, small irregular notches or V-shaped nicks.
            - left_ear_tnr_mark / right_ear_tnr_mark: true only when that ear is clearly visible AND shows the straight \
            flat cut; false if intact, hidden or unclear.
            - ear_tip_verdict: "yes" only if one ear clearly shows the cut; "no" only if BOTH ear tips are clearly \
            visible and intact; otherwise "unclear". A wrong "yes" is the worst mistake — when in doubt use "unclear".
            - ear_tip_confidence_percent: integer 0–100 for the verdict; ear_tip_basis: one Simplified Chinese \
            sentence describing the left and the right ear; ear_tip_basis_en: the same in English.
            - ear_tip_detected and likelyEarTipped: true only when ear_tip_verdict is "yes".
            - ear_tip_confidence: "high" | "medium" | "low"; must be "low" when the verdict is "unclear".

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
              "ear_tip_basis": "<one short sentence in Simplified Chinese, covering left and right ear>",
              "ear_tip_basis_en": "<the same sentence in English>",
              "health_body_normal_percent": <integer 0-100>,
              "health_undernutrition_percent": <integer 0-100>,
              "health_severely_emaciated_percent": <integer 0-100>,
              "health_suspected_injury_percent": <integer 0-100>,
              "image_quality": "<high|medium|low>",
              "indoor_clean_setting": <true|false>,
              "acute_symptoms_visible": <true|false>,
              "rationalePhrases": [<2-4 short phrases in Simplified Chinese; use 无法判断 when unclear>],
              "rationalePhrasesEn": [<the same phrases in English, same order; use "cannot determine" when unclear>]
            }

            LANGUAGE: rationalePhrases and ear_tip_basis must be entirely Simplified Chinese; rationalePhrasesEn and \
            ear_tip_basis_en must be entirely English with the same meaning. Never mix languages inside one field.

            Omit sickOrInjuredConfidence from your output — the server will derive a legacy score from the four health percentages.
            needsFeedingConfidence must always be exactly 0.

            likelyEarTipped must equal (ear_tip_verdict == "yes").
            """;

    /** Sent before the optional second image (auto-detected head crop). */
    public static final String HEAD_CROP_NOTE = """
            Image 2 is an automatic close-up crop of the same cat's head, upscaled for ear inspection. \
            Use it as the primary evidence for left_ear_tnr_mark, right_ear_tnr_mark and all ear_tip_* fields: \
            a TNR ear tip is a straight, flat cut across the top of one ear (either side; conventions differ by region), not a rounded notch or fold. \
            The crop is automatic and may be imperfect — if it does not show this cat's head, ignore it and use image 1.
            """;

    /** Same as {@link #HEAD_CROP_NOTE}, worded for the tracking feature-extraction schema (earTipped fields). */
    public static final String HEAD_CROP_NOTE_FEATURES = """
            Image 2 is an automatic close-up crop of the same cat's head, upscaled for ear inspection. \
            Use it as the primary evidence for earTipped and earTippedConfidence: \
            a TNR ear tip is a straight, flat cut across the top of one ear (either side; conventions differ by region), not a rounded notch or fold. \
            Use image 1 for coat, pattern, body size and other features. \
            The crop is automatic and may be imperfect — if it does not show this cat's head, ignore it.
            """;

    private VisionPrompts() {
    }
}
