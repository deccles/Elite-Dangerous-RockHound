package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TransportRoutePlannerTest {

    @Test
    void splitsShipmentAcrossCapacityLimitedReturnTrips() {
        TransportLocation start = location("Start", "Start Port", 0);
        TransportLocation source = location("Source", "Source Port", 10);
        TransportLocation destination = location("Destination", "Delivery Port", 20);
        TransportShipment shipment = TransportShipment.cargo(
                1L, "Gold", 120, 0, source, destination);

        TransportRoutePlan plan = TransportRoutePlanner.plan(
                new TransportPlanRequest(start, 100, 0, List.of(shipment)));

        assertTrue(plan.optimal());
        assertEquals(40.0, plan.totalDistanceLy(), 0.0001);
        assertEquals(List.of(
                "PICK_UP 100 Gold",
                "DELIVER 100 Gold",
                "PICK_UP 20 Gold",
                "DELIVER 20 Gold"),
                plan.stops().stream()
                        .flatMap(stop -> stop.actions().stream())
                        .map(action -> action.kind() + " " + action.tons() + " " + action.commodity())
                        .toList());
        assertEquals(List.of(100, 0, 20, 0),
                plan.stops().stream().map(TransportPlanStop::holdAfterTons).toList());
    }

    @Test
    void allocatesSharedPickupCapacityToTheGloballyShorterRoute() {
        TransportLocation start = location("Start", "Start Port", 0);
        TransportLocation source = location("Source", "Source Port", 10);
        TransportShipment far = TransportShipment.cargo(
                1L, "Gold", 60, 0, source, location("Far", "Far Port", 100));
        TransportShipment near = TransportShipment.cargo(
                2L, "Silver", 60, 0, source, location("Near", "Near Port", 20));

        TransportRoutePlan plan = TransportRoutePlanner.plan(
                new TransportPlanRequest(start, 100, 0, List.of(far, near)));

        assertTrue(plan.optimal());
        assertEquals(120.0, plan.totalDistanceLy(), 0.0001);
        assertEquals(List.of("Source", "Near", "Source", "Far"),
                plan.stops().stream().map(stop -> stop.location().system()).toList());
        assertEquals(List.of(100, 40, 60, 0),
                plan.stops().stream().map(TransportPlanStop::holdAfterTons).toList());
    }

    @Test
    void includesCargoFreeTransportStopsInShortestOrder() {
        TransportLocation start = location("Start", "Start Port", 0);
        TransportVisit far = new TransportVisit(3L, "Passenger", location("Far", "Far Port", 30));
        TransportVisit near = new TransportVisit(4L, "Courier", location("Near", "Near Port", 10));

        TransportRoutePlan plan = TransportRoutePlanner.plan(
                new TransportPlanRequest(start, 100, 0, List.of(), List.of(far, near)));

        assertEquals(30.0, plan.totalDistanceLy(), 0.0001);
        assertEquals(List.of("Near", "Far"),
                plan.stops().stream().map(stop -> stop.location().system()).toList());
        assertEquals(List.of(TransportPlanAction.Kind.VISIT, TransportPlanAction.Kind.VISIT),
                plan.stops().stream().flatMap(stop -> stop.actions().stream())
                        .map(TransportPlanAction::kind).toList());
    }

    private static TransportLocation location(String system, String station, double x) {
        return new TransportLocation(system, station, x, 0, 0);
    }
}
