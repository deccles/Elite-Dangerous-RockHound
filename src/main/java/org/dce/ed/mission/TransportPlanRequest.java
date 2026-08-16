package org.dce.ed.mission;

import java.util.List;

/** Validated inputs for a Transport stop optimization. */
public record TransportPlanRequest(TransportLocation start, int cargoCapacity, int occupiedCargo,
        List<TransportShipment> shipments, List<TransportVisit> visits) {
    public TransportPlanRequest(TransportLocation start, int cargoCapacity, int occupiedCargo,
            List<TransportShipment> shipments) {
        this(start, cargoCapacity, occupiedCargo, shipments, List.of());
    }

    public TransportPlanRequest {
        if (start == null || cargoCapacity <= 0 || occupiedCargo < 0 || occupiedCargo > cargoCapacity) {
            throw new IllegalArgumentException("Invalid transport plan capacity or start");
        }
        shipments = List.copyOf(shipments);
        visits = List.copyOf(visits);
    }
}
