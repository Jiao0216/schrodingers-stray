package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionJsonMapperTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void rightEarMarkOverridesVerdictNo() throws Exception {
        String json = """
                {"sickOrInjuredConfidence":0.1,"needsFeedingConfidence":0.2,"likelyNotNeuteredOrEarNotTippedConfidence":0.5,
                "rationalePhrases":["x"],"likelyEarTipped":false,"ear_tip_detected":false,
                "ear_tip_verdict":"no","left_ear_tnr_mark":false,"right_ear_tnr_mark":true,
                "ear_tip_confidence":"medium","ear_tip_confidence_percent":80,"ear_tip_basis":"右耳平剪可见"}
                """;
        ModelLabels m = VisionJsonMapper.parse(M.readTree(json));
        assertTrue(m.likelyEarTipped());
        assertTrue(m.earTipDetected());
    }

    @Test
    void leftEarMarkOverridesVerdictNo() throws Exception {
        String json = """
                {"sickOrInjuredConfidence":0.1,"needsFeedingConfidence":0.2,"likelyNotNeuteredOrEarNotTippedConfidence":0.5,
                "rationalePhrases":["x"],"likelyEarTipped":false,"ear_tip_detected":false,
                "ear_tip_verdict":"no","left_ear_tnr_mark":true,"right_ear_tnr_mark":false,
                "ear_tip_confidence":"low","ear_tip_confidence_percent":45,"ear_tip_basis":"左耳见平剪"}
                """;
        ModelLabels m = VisionJsonMapper.parse(M.readTree(json));
        assertTrue(m.likelyEarTipped());
    }

    @Test
    void verdictYesWithoutFlagsStillTipped() throws Exception {
        String json = """
                {"sickOrInjuredConfidence":0.1,"needsFeedingConfidence":0.2,"likelyNotNeuteredOrEarNotTippedConfidence":0.1,
                "rationalePhrases":["x"],"likelyEarTipped":true,"ear_tip_detected":true,
                "ear_tip_verdict":"yes","left_ear_tnr_mark":false,"right_ear_tnr_mark":false,
                "ear_tip_confidence":"high","ear_tip_confidence_percent":85,"ear_tip_basis":"剪耳可见"}
                """;
        ModelLabels m = VisionJsonMapper.parse(M.readTree(json));
        assertTrue(m.likelyEarTipped());
    }

    @Test
    void noMarksAndVerdictNoStaysNotTipped() throws Exception {
        String json = """
                {"sickOrInjuredConfidence":0.1,"needsFeedingConfidence":0.2,"likelyNotNeuteredOrEarNotTippedConfidence":0.6,
                "rationalePhrases":["intact ears"],"likelyEarTipped":false,"ear_tip_detected":false,
                "ear_tip_verdict":"no","left_ear_tnr_mark":false,"right_ear_tnr_mark":false,
                "ear_tip_confidence":"high","ear_tip_confidence_percent":90,"ear_tip_basis":"双耳完整"}
                """;
        ModelLabels m = VisionJsonMapper.parse(M.readTree(json));
        assertFalse(m.likelyEarTipped());
    }
}
