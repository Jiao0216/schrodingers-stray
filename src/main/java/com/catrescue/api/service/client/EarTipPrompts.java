package com.catrescue.api.service.client;

/**
 * Prompt for the dedicated ear-tip (TNR clip) check. It asks one narrow question so the answer is not diluted by
 * the dozen health fields of the main analysis, and it spells out the usual false positives.
 */
public final class EarTipPrompts {

    public static final String SYSTEM = """
            You are a careful animal-welfare vision assistant. You answer one narrow question about one cat and \
            return strict JSON. When the evidence is not clear you say so instead of guessing.""";

    public static final String USER = """
            Task: decide whether this cat has a TNR ear tip.

            A TNR ear tip: the top of ONE ear (about 1 cm, roughly the top quarter of the ear) has been removed with a \
            straight, flat cut, leaving a squared-off top edge. It can be on either ear.

            These are NOT ear tips:
            - naturally pointed or rounded ear tips, fur tufts on the tips
            - ears folded back, turned sideways, or seen edge-on
            - ears partly hidden by the head, fur, objects, or the image border
            - blur, low resolution, compression artifacts, shadows or bright edges
            - small irregular notches, V-shaped nicks or tears (fight injuries)

            Steps:
            1. For each ear (left / right as seen in the image) choose exactly one:
               "tipped" — a straight flat cut is clearly visible;
               "intact" — the complete natural tip is clearly visible;
               "not_visible" — you cannot see the tip well enough to tell.
            2. verdict:
               "yes" only if at least one ear is "tipped";
               "no" only if BOTH ears are "intact";
               otherwise "unclear".
            A wrong "yes" is the worst mistake (the cat would be skipped for TNR) — if in doubt, answer "unclear".

            {IMAGES}

            Return JSON only, with exactly these keys:
            {
              "left_ear": "tipped|intact|not_visible",
              "right_ear": "tipped|intact|not_visible",
              "verdict": "yes|no|unclear",
              "confidence_percent": <integer 0-100, how sure you are of the verdict>,
              "basis_zh": "<一句简体中文：分别说明左耳、右耳看到了什么>",
              "basis_en": "<the same sentence in English>"
            }""";

    public static final String IMAGES_WITH_CROP = """
            Image 1 is an automatic, upscaled close-up of the cat's head — use it as the main evidence. \
            Image 2 is the full photo for context. If image 1 does not show this cat's head, ignore it.""";

    public static final String IMAGES_FULL_ONLY = "There is one image: the full photo.";

    private EarTipPrompts() {
    }

    public static String user(boolean withHeadCrop) {
        return USER.replace("{IMAGES}", withHeadCrop ? IMAGES_WITH_CROP : IMAGES_FULL_ONLY);
    }
}
