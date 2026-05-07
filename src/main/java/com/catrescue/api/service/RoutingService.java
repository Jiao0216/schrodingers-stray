package com.catrescue.api.service;

import com.catrescue.api.domain.BranchType;
import com.catrescue.api.domain.ModelLabels;
import com.catrescue.api.config.RoutingProperties;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

    private final RoutingProperties props;

    public RoutingService(RoutingProperties props) {
        this.props = props;
    }

    private static final int STRONG_HEALTH_SIGNAL = 70;

    /**
     * Health-first routing (no “hunger / feeding outreach” branch):
     * 1) Suspected injury (high confidence) → rescue / shelters.
     * 2) Severe emaciation or undernutrition (high confidence) → TNR / welfare follow-up.
     * 3) Legacy combined score still triggers rescue when above threshold.
     * 4) Otherwise TNR resources if intact-ear signal is strong, else general guidance.
     */
    public BranchType decide(ModelLabels labels) {
        double rescueLegacy = labels.sickOrInjuredConfidence();
        double tnr = labels.likelyNotNeuteredOrEarNotTippedConfidence();

        int inj = labels.healthSuspectedInjuryPercent();
        int sev = labels.healthSeverelyEmaciatedPercent();
        int und = labels.healthUndernutritionPercent();
        boolean hasPercents = inj >= 0 || sev >= 0 || und >= 0;

        boolean injuryHit = inj >= STRONG_HEALTH_SIGNAL;
        boolean severeHit = sev >= STRONG_HEALTH_SIGNAL;
        boolean underHit = und >= STRONG_HEALTH_SIGNAL;
        boolean rescueHit = rescueLegacy >= props.getRescueThreshold();
        boolean tnrHit = tnr >= props.getTnrThreshold();

        if (hasPercents) {
            if (injuryHit) {
                return BranchType.RESCUE;
            }
            if (severeHit || underHit) {
                return BranchType.TNR_RESOURCES;
            }
        } else if (rescueHit) {
            return BranchType.RESCUE;
        }

        if (tnrHit) {
            return BranchType.TNR_RESOURCES;
        }
        return BranchType.GENERAL_GUIDANCE;
    }
}
