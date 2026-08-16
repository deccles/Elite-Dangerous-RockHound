package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class TransportPlanPreparerTest {
    @Test
    void blocksTheEntirePlanWhenASelfSourcedMissionHasNoSource() {
        MissionRecord mission = sourcedMission(1L, "Gold", 20);

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, null,
                system -> { throw new AssertionError("coordinates must not be requested"); });

        assertNull(result.request());
        assertEquals(List.of(TransportPlanProblem.Code.SOURCE_REQUIRED),
                result.problems().stream().map(TransportPlanProblem::code).toList());
        assertEquals(1L, result.problems().get(0).missionId());
    }

    @Test
    void buildsCargoShipmentFromEnteredSourceAndMissionTaggedHold() {
        MissionRecord mission = sourcedMission(7L, "Gold", 20);
        mission.setSourcedFromSystem("Lave");
        mission.setSourcedFromStation("Lave Station");
        var cargo = JsonParser.parseString("""
                {"Inventory":[{"Name":"gold","Name_Localised":"Gold","Count":8,"MissionID":7}]}
                """).getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, cargo,
                system -> switch (system) {
                    case "Sol" -> new double[] { 0, 0, 0 };
                    case "Lave" -> new double[] { 10, 0, 0 };
                    case "Achenar" -> new double[] { 20, 0, 0 };
                    default -> null;
                });

        assertTrue(result.problems().isEmpty());
        assertEquals(8, result.request().occupiedCargo());
        TransportShipment shipment = result.request().shipments().get(0);
        assertEquals(20, shipment.tonsRemaining());
        assertEquals(8, shipment.tonsAboard());
        assertEquals("Lave Station", shipment.pickup().station());
        assertEquals("Achenar", shipment.delivery().system());
    }

    @Test
    void blocksPlanningUntilLiveCargoIsAvailable() {
        MissionRecord mission = sourcedMission(8L, "Gold", 20);
        mission.setSourcedFromSystem("Lave");
        mission.setSourcedFromStation("Lave Station");

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, null,
                system -> { throw new AssertionError("coordinates must not be requested"); });

        assertNull(result.request());
        assertEquals(List.of(TransportPlanProblem.Code.CARGO_REQUIRED),
                result.problems().stream().map(TransportPlanProblem::code).toList());
    }

    @Test
    void ordinaryCargoDoesNotReplaceMissionTaggedDeliveryCargo() {
        MissionRecord mission = new MissionRecord(9L);
        mission.setName("Mission_Delivery_Boom");
        mission.setCommodityLocalised("Gold");
        mission.setCountRequired(20);
        mission.setOriginSystem("Lave");
        mission.setOriginStation("Lave Station");
        mission.setDestinationSystem("Achenar");
        mission.setDestinationStation("Dawes Hub");
        var cargo = JsonParser.parseString("""
                {"Inventory":[{"Name":"gold","Name_Localised":"Gold","Count":8}]}
                """).getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, cargo,
                system -> switch (system) {
                    case "Sol" -> new double[] { 0, 0, 0 };
                    case "Lave" -> new double[] { 10, 0, 0 };
                    case "Achenar" -> new double[] { 20, 0, 0 };
                    default -> null;
                });

        assertTrue(result.problems().isEmpty());
        TransportShipment shipment = result.request().shipments().get(0);
        assertEquals(0, shipment.tonsAboard());
        assertEquals("Lave Station", shipment.pickup().station());
    }

    @Test
    void cargoTaggedToAnotherMissionIsNotLooseCargo() {
        MissionRecord mission = sourcedMission(10L, "Gold", 20);
        mission.setSourcedFromSystem("Lave");
        mission.setSourcedFromStation("Lave Station");
        var cargo = JsonParser.parseString("""
                {"Inventory":[{"Name":"gold","Name_Localised":"Gold","Count":8,"MissionID":999}]}
                """).getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, cargo,
                system -> switch (system) {
                    case "Sol" -> new double[] { 0, 0, 0 };
                    case "Lave" -> new double[] { 10, 0, 0 };
                    case "Achenar" -> new double[] { 20, 0, 0 };
                    default -> null;
                });

        assertTrue(result.problems().isEmpty());
        assertEquals(0, result.request().shipments().get(0).tonsAboard());
    }

    private static MissionRecord sourcedMission(long id, String commodity, int count) {
        MissionRecord mission = new MissionRecord(id);
        mission.setName("Mission_Sourced_Boom");
        mission.setCommodityLocalised(commodity);
        mission.setCountRequired(count);
        mission.setDestinationSystem("Achenar");
        mission.setDestinationStation("Dawes Hub");
        return mission;
    }
}
