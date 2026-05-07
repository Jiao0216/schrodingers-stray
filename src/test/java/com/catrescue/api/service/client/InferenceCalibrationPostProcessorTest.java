package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceCalibrationPostProcessorTest {

    @Test
    void jsonEarTipForcesLowTnr() {
        ModelLabels in = new ModelLabels(
                0.4,
                0.3,
                0.7,
                List.of("test"),
                false,
                true,
                "high",
                "medium",
                false,
                false,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = InferenceCalibrationPostProcessor.apply(in);
        assertTrue(out.likelyEarTipped());
        assertTrue(out.likelyNotNeuteredOrEarNotTippedConfidence() <= 0.06);
    }

    @Test
    void lowImageQualityWithoutAcuteCapsSick() {
        ModelLabels in = new ModelLabels(
                0.75,
                0.2,
                0.3,
                List.of("x"),
                false,
                false,
                "",
                "low",
                false,
                false,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = InferenceCalibrationPostProcessor.apply(in);
        assertTrue(out.sickOrInjuredConfidence() <= 0.40);
    }

    @Test
    void indoorReducesSick() {
        ModelLabels in = new ModelLabels(
                0.4,
                0.2,
                0.3,
                List.of("x"),
                false,
                false,
                "",
                "high",
                true,
                true,
                -1,
                "",
                -1, -1, -1, -1
        );
        ModelLabels out = InferenceCalibrationPostProcessor.apply(in);
        assertEquals(0.34, out.sickOrInjuredConfidence(), 0.01);
    }
}
