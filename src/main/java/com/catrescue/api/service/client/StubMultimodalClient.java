package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Deterministic stub from image bytes — swap for WebClient/Feign to a real inference endpoint.
 */
public class StubMultimodalClient implements MultimodalClient {

    @Override
    public ModelLabels analyzeImageBytes(byte[] imageBytes, String contentType) {
        long seed = Arrays.hashCode(imageBytes);
        Random rnd = new Random(seed);

        double tnr = 0.12 + 0.65 * rnd.nextDouble();
        int hNormal = rnd.nextInt(35) + 40;
        int hUnder = rnd.nextInt(40);
        int hSevere = rnd.nextInt(25);
        int hInjury = rnd.nextInt(30);
        double sick = Math.max(Math.max(hInjury, hSevere), hUnder) / 100.0;

        List<String> rationale = new ArrayList<>();
        if (hInjury >= 55) {
            rationale.add("Stub: possible injury or abnormal mobility cues");
        }
        if (hSevere >= 50) {
            rationale.add("Stub: possible severe body condition — verify in person");
        }
        if (hUnder >= 50) {
            rationale.add("Stub: possible undernutrition cues");
        }
        if (tnr > 0.52) {
            rationale.add("Stub: ear tipping not clearly visible — TNR eligibility may apply");
        }
        if (rationale.isEmpty()) {
            rationale.add("Stub: no strong single label — verify in person");
        }

        return InferenceCalibrationPostProcessor.apply(ModelLabelPostProcessor.adjustForEarTipEvidence(
                new ModelLabels(sick, 0, tnr, List.copyOf(rationale), false, false, "", "medium", false, false, -1, "",
                        hNormal, hUnder, hSevere, hInjury)
        ));
    }
}
