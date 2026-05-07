package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelLabelPostProcessorTest {

    @Test
    void chineseEarClipRaisesTippedFlagAndCapsTnr() {
        ModelLabels in = new ModelLabels(
                0.2,
                0.2,
                0.85,
                List.of("可见剪耳，耳尖平"),
                false,
                false,
                "",
                "medium",
                false,
                false,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = ModelLabelPostProcessor.adjustForEarTipEvidence(in);
        assertTrue(out.likelyEarTipped());
        assertTrue(out.likelyNotNeuteredOrEarNotTippedConfidence() <= 0.10);
    }

    @Test
    void negationNotEarClippedDoesNotInferTipFromSubstring() {
        ModelLabels in = new ModelLabels(
                0.2,
                0.2,
                0.4,
                List.of("双耳未见剪耳，耳朵完整"),
                false,
                false,
                "",
                "medium",
                false,
                false,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = ModelLabelPostProcessor.adjustForEarTipEvidence(in);
        assertFalse(out.likelyEarTipped());
    }

    @Test
    void intactEarsBoostTnr() {
        ModelLabels in = new ModelLabels(
                0.2,
                0.2,
                0.15,
                List.of("ears intact, symmetrical ear tips"),
                false,
                false,
                "",
                "medium",
                false,
                false,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = ModelLabelPostProcessor.adjustForEarTipEvidence(in);
        assertFalse(out.likelyEarTipped());
        assertTrue(out.likelyNotNeuteredOrEarNotTippedConfidence() >= 0.55);
    }
}
