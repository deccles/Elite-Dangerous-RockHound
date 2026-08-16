package org.dce.ed.mission;

import java.util.List;

/** One station visit and the projected hold after its actions. */
public record TransportPlanStop(TransportLocation location, List<TransportPlanAction> actions,
        int holdAfterTons) {
    public TransportPlanStop {
        actions = List.copyOf(actions);
    }
}
