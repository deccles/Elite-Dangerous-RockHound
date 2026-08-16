package org.dce.ed.mission;

import java.util.List;

/** Immutable result of a cargo-aware Transport route calculation. */
public record TransportRoutePlan(List<TransportPlanStop> stops, double totalDistanceLy,
        boolean optimal) {
    public TransportRoutePlan {
        stops = List.copyOf(stops);
    }
}
