package com.catrescue.api.service.client;

import com.catrescue.api.domain.ModelLabels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelLabelPostProcessorLowTnrTest {

    @Test
    void veryLowTnrWithoutEvidenceDoesNotForceEarTipped() {
        ModelLabels in = new ModelLabels(
                0.2,
                0.2,
                0.06,
                List.of("Outdoor daytime scene"),
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
}
