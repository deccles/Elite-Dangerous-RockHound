package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class TransportPlanPreparerTest {
    @Test
    void donationMissionCreatesAnActionablePaymentStop() {
        MissionRecord mission = new MissionRecord(77L);
        mission.setName("Mission_Altruism");
        mission.setDonation(500_000L);
        mission.setDestinationSystem("Lave");
        mission.setDestinationStation("Lave Station");
        var cargo = JsonParser.parseString("{\"Inventory\":[]}").getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, cargo,
                system -> switch (system) {
                    case "Sol" -> new double[] { 0, 0, 0 };
                    case "Lave" -> new double[] { 10, 0, 0 };
                    default -> null;
                });

        assertTrue(result.problems().isEmpty());
        assertEquals(1, result.request().visits().size());
        assertEquals("Donate 500,000 Cr", result.request().visits().get(0).label());
    }
    @Test
    void missingSourceWarnsWithoutCreatingANonActionableDeliveryStop() {
        MissionRecord mission = sourcedMission(1L, "Gold", 20);
        var cargo = JsonParser.parseString("{\"Inventory\":[]}").getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Sol", "Galileo", 64, cargo,
                system -> switch (system) {
                    case "Sol" -> new double[] { 0, 0, 0 };
                    case "Achenar" -> new double[] { 20, 0, 0 };
                    default -> throw new AssertionError("unknown source must not be resolved");
                });

        assertTrue(result.problems().isEmpty());
        assertEquals(0, result.request().shipments().size());
        assertEquals(0, result.request().visits().size());
        List<TransportPlanProblem> warnings = warnings(result);
        assertEquals(1, warnings.size());
        assertEquals(TransportPlanProblem.Code.SOURCE_REQUIRED, warnings.get(0).code());
        assertTrue(warnings.get(0).message().contains("Gold"));
        assertTrue(warnings.get(0).message().contains("Pickup not planned"));
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

    @Test
    void explainsWhenUnrelatedCargoLeavesNoSpaceForPendingPickups() {
        MissionRecord mission = sourcedMission(11L, "Food Cartridges", 1190);
        mission.setSourcedFromSystem("Core Sys Sector FW-N a6-0");
        mission.setSourcedFromStation("Davy Vision");
        var cargo = JsonParser.parseString("""
                {"Inventory":[
                  {"Name":"gold","Name_Localised":"Gold","Count":138},
                  {"Name":"animalmonitors","Name_Localised":"Animal Monitors","Count":918}
                ]}
                """).getAsJsonObject();

        TransportPlanPreparation result = TransportPlanPreparer.prepare(
                List.of(mission), "Gliese 868", "MacLean Terminal", 1056, cargo,
                system -> switch (system) {
                    case "Gliese 868" -> new double[] { 0, 0, 0 };
                    case "Core Sys Sector FW-N a6-0" -> new double[] { 10, 0, 0 };
                    case "Achenar" -> new double[] { 20, 0, 0 };
                    default -> null;
                });

        assertNull(result.request());
        assertEquals(1, result.problems().size());
        TransportPlanProblem problem = result.problems().get(0);
        assertEquals(TransportPlanProblem.Code.CARGO_SPACE_REQUIRED, problem.code());
        assertTrue(problem.message().contains("1,056 t"));
        assertTrue(problem.message().contains("not assigned to these missions"));
        assertTrue(problem.message().contains("Sell or discard"));
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

    @SuppressWarnings("unchecked")
    private static List<TransportPlanProblem> warnings(TransportPlanPreparation preparation) {
        try {
            Method method = preparation.getClass().getMethod("warnings");
            return (List<TransportPlanProblem>) method.invoke(preparation);
        } catch (ReflectiveOperationException ex) {
            fail("Transport plan preparation should expose advisory warnings", ex);
            return List.of();
        }
    }
}
