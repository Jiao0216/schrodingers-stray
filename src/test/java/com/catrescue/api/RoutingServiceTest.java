package com.catrescue.api;

import com.catrescue.api.config.RoutingProperties;
import com.catrescue.api.domain.BranchType;
import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.service.RoutingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingServiceTest {

    @Test
    void rescueWinsWhenAboveThreshold() {
        RoutingProperties p = new RoutingProperties();
        RoutingService svc = new RoutingService(p);
        ModelLabels labels = new ModelLabels(0.9, 0.9, 0.9, List.of(), false, false, "", "medium", false, false, -1, "", -1, -1, -1, -1);
        assertThat(svc.decide(labels)).isEqualTo(BranchType.RESCUE);
    }

    @Test
    void tnrWhenRescueBelowAndTnrHigherThanFeeding() {
        RoutingProperties p = new RoutingProperties();
        RoutingService svc = new RoutingService(p);
        ModelLabels labels = new ModelLabels(0.2, 0.5, 0.9, List.of(), false, false, "", "medium", false, false, -1, "", -1, -1, -1, -1);
        assertThat(svc.decide(labels)).isEqualTo(BranchType.TNR_RESOURCES);
    }

    @Test
    void rescueWhenSuspectedInjuryPercentHigh() {
        RoutingProperties p = new RoutingProperties();
        RoutingService svc = new RoutingService(p);
        ModelLabels labels = new ModelLabels(0.2, 0, 0.3, List.of(), false, false, "", "medium", false, false, -1, "", 40, 10, 5, 75);
        assertThat(svc.decide(labels)).isEqualTo(BranchType.RESCUE);
    }

    @Test
    void tnrWhenOnlyTnrAbove() {
        RoutingProperties p = new RoutingProperties();
        RoutingService svc = new RoutingService(p);
        ModelLabels labels = new ModelLabels(0.2, 0.2, 0.5, List.of(), false, false, "", "medium", false, false, -1, "", -1, -1, -1, -1);
        assertThat(svc.decide(labels)).isEqualTo(BranchType.TNR_RESOURCES);
    }
}
