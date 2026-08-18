package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
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

    @Test
    void plansManyMissionsImmediatelyWhenAllCargoFitsInOneTrip() {
        TransportLocation start = location("Start", "Start Port", 0);
        TransportLocation source = location("Source", "Source Port", 10);
        TransportLocation destination = location("Destination", "Delivery Port", 20);
        List<TransportShipment> shipments = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            shipments.add(TransportShipment.cargo(id, "Commodity " + id, 50, 0, source, destination));
        }

        TransportRoutePlan plan = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> TransportRoutePlanner.plan(
                        new TransportPlanRequest(start, 1000, 0, shipments)));

        assertEquals(List.of("Source", "Destination"),
                plan.stops().stream().map(stop -> stop.location().system()).toList());
        assertEquals(600, plan.stops().get(0).holdAfterTons());
        assertEquals(12, plan.stops().get(0).actions().size());
        assertEquals(12, plan.stops().get(1).actions().size());
    }

    @Test
    void plansEquivalentMissionsImmediatelyWhenTheyRequireMultipleTrips() {
        TransportLocation start = location("Start", "Start Port", 0);
        TransportLocation source = location("Source", "Source Port", 10);
        TransportLocation destination = location("Destination", "Delivery Port", 20);
        List<TransportShipment> shipments = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            shipments.add(TransportShipment.cargo(id, "Commodity " + id, 100, 0, source, destination));
        }

        TransportRoutePlan plan = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> TransportRoutePlanner.plan(
                        new TransportPlanRequest(start, 500, 0, shipments)));

        assertEquals(List.of("Source", "Destination", "Source", "Destination", "Source", "Destination"),
                plan.stops().stream().map(stop -> stop.location().system()).toList());
        assertEquals(1200, plan.stops().stream().flatMap(stop -> stop.actions().stream())
                .filter(action -> action.kind() == TransportPlanAction.Kind.DELIVER)
                .mapToInt(TransportPlanAction::tons).sum());
    }

    @Test
    void plansCurrentTwoDestinationThirteenMissionWorkloadImmediately() {
        TransportLocation start = location("Gliese 868", "Bacon Port", 0);
        TransportLocation source = location("Core Sys Sector EW-N a6-1", "Gilmore Legacy", 16.2);
        TransportLocation bacon = location("Gliese 868", "Bacon Port", 0);
        TransportLocation macLean = location("Gliese 868", "MacLean Terminal", 0);
        int[] baconTons = { 63, 72, 18, 959, 90, 1035, 72, 45, 18, 16 };
        int[] macLeanTons = { 891, 42, 81 };
        List<TransportShipment> shipments = new ArrayList<>();
        long id = 1;
        for (int tons : baconTons)
            shipments.add(TransportShipment.cargo(id++, "Cargo " + id, tons, 0, source, bacon));
        for (int tons : macLeanTons)
            shipments.add(TransportShipment.cargo(id++, "Cargo " + id, tons, 0, source, macLean));

        TransportRoutePlan plan = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> TransportRoutePlanner.plan(new TransportPlanRequest(start, 1056, 0, shipments)));

        assertEquals(3402, plan.stops().stream().flatMap(stop -> stop.actions().stream())
                .filter(action -> action.kind() == TransportPlanAction.Kind.DELIVER)
                .mapToInt(TransportPlanAction::tons).sum());
    }

    private static TransportLocation location(String system, String station, double x) {
        return new TransportLocation(system, station, x, 0, 0);
    }
}
