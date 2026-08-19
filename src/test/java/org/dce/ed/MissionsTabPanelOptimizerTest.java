package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanActionCompletion;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportRoutePlan;
import org.dce.ed.mission.TransportPlanRequest;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.ui.TransportRoutePlanPanel;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.Gson;

class MissionsTabPanelOptimizerTest {
    @Test
    void undockingWarnsAboutUnfinishedWorkAtTheCurrentPlanStop() {
        AtomicReference<String> spoken = new AtomicReference<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Lave", () -> "Lave Station",
                () -> 64, systems -> { });
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);
        invoke(panel, "updateOptimizedPlanLocationHighlight",
                new Class<?>[] { String.class, String.class }, "Lave", "Lave Station");
        writeField(panel, "latestJournalSystem", "Lave");
        writeField(panel, "latestJournalStation", "Lave Station");
        writeField(panel, "departureReminderSpeaker", (Consumer<String>) spoken::set);

        panel.handleLogEvent(new EliteLogEvent.GenericEvent(
                Instant.now(), EliteEventType.UNDOCKED, new JsonObject()));

        assertEquals("Did you forget your delivery again, Commander?", spoken.get());
    }
    @Test
    void transportTabProvidesOptimizeStopsButton() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });

        assertNotNull(findButton(panel, "Create Plan"));
    }

    @Test
    void transportNavigationLetsAnEmptyPlanTabCreateThePlanInPlace() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });

        assertNotNull(findButton(panel, "Transport Missions"));
        JButton optimizedPlan = findButton(panel, "Optimized Plan");
        assertNotNull(optimizedPlan);
        assertTrue(optimizedPlan.isEnabled());
        optimizedPlan.doClick();
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
        assertNotNull(findLabelContaining(panel, "No transport plan yet"));
        assertNotNull(findButton(panel, "Create Plan"));
        assertNull(findButton(panel, "Cargo"));
        assertNull(findButton(panel, "Courier"));
        assertNull(findButton(panel, "Passenger"));
    }

    @Test
    void transportMissionsShowsSourceReadinessAndAResolveMissingSourcesAction() throws Exception {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        panel.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:00:00Z","event":"MissionAccepted",
                 "Name":"Mission_Sourced_Boom","MissionID":88,
                 "Commodity_Localised":"Gold","Count":12,
                 "DestinationSystem":"Lave","DestinationStation":"Lave Station"}
                """));
        SwingUtilities.invokeAndWait(() -> { });

        assertNotNull(findLabelContaining(panel, "1 mission · 0 ready · 1 needs source"));
        assertNotNull(findButton(panel, "Resolve 1 Missing Source"));
    }

    @Test
    void changingASourceMarksThePlanOutOfDateWithoutRerouting() throws Exception {
        AtomicReference<String> spoken = new AtomicReference<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        panel.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:00:00Z","event":"MissionAccepted",
                 "Name":"Mission_Sourced_Boom","MissionID":88,
                 "Commodity_Localised":"Gold","Count":12,
                 "DestinationSystem":"Lave","DestinationStation":"Lave Station"}
                """));
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.DELIVER, 88L, "Gold", 12)), 0)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);
        writeField(panel, "planStatusSpeaker", (Consumer<String>) spoken::set);

        assertTrue(panel.applySourcedFromSelection(88L, "Leesti", "George Lucas"));

        assertNull(spoken.get());
        assertNotNull(findLabelContaining(panel, "Plan out of date"));
    }

    @Test
    void cargoDepotProgressMarksThePlanOutOfDateWithoutReplacingIt() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        panel.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:00:00Z","event":"MissionAccepted",
                 "Name":"Mission_Collect","MissionID":88,
                 "Commodity_Localised":"Gold","Count":12,
                 "DestinationSystem":"Lave","DestinationStation":"Lave Station"}
                """));
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Sol", "Galileo", 0, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.PICK_UP, 88L, "Gold", 12)), 12)), 0.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        panel.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:01:00Z","event":"CargoDepot","MissionID":88,
                 "UpdateType":"Collect","CargoType":"Gold","Count":12,
                 "StartMarketID":1,"EndMarketID":2,"ItemsCollected":12,
                 "ItemsDelivered":0,"TotalItemsToDeliver":12}
                """));

        assertNotNull(findLabelContaining(panel, "Plan out of date"));
    }

    @Test
    void reroutingPreservesCompletedActionsWhenStopsMove() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportPlanStop lave = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(new TransportPlanAction(TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0);
        TransportPlanStop leesti = new TransportPlanStop(
                new TransportLocation("Leesti", "George Lucas", 20, 0, 0),
                List.of(new TransportPlanAction(TransportPlanAction.Kind.VISIT, 2L, "Courier", 0)), 0);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class },
                new TransportRoutePlan(List.of(lave, leesti), 20.0, true));
        invoke(panel, "updateOptimizedPlanLocationHighlight",
                new Class<?>[] { String.class, String.class }, "Lave", "Lave Station");
        panel.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:05:00Z","event":"MissionCompleted","MissionID":1}
                """));

        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class },
                new TransportRoutePlan(List.of(leesti, lave), 20.0, true));

        javax.swing.JTable table = findTableWithColumn(panel, "Action");
        assertNotNull(table);
        assertEquals(true, table.getValueAt(2, 0));
    }

    @Test
    void reroutingDoesNotMoveACompletionToADifferentSplitPickup() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 1056, systems -> { });
        TransportLocation rockVision = new TransportLocation(
                "Core Sys Sector AQ-P a5-1", "Rock Vision", 10, 0, 0);
        TransportRoutePlan oldPlan = new TransportRoutePlan(List.of(
                new TransportPlanStop(rockVision,
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.PICK_UP, 88L,
                                "Advanced Medicines", 54)), 54)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, oldPlan);
        TransportRoutePlanPanel oldPanel = (TransportRoutePlanPanel) readField(panel, "optimizedPlanPanel");
        oldPanel.restoreCompletedActionCompletions(List.of(
                new TransportPlanActionCompletion(0, TransportPlanAction.Kind.PICK_UP, 88L)));

        TransportRoutePlan revised = new TransportRoutePlan(List.of(
                new TransportPlanStop(rockVision,
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.PICK_UP, 88L,
                                "Advanced Medicines", 523)), 523),
                new TransportPlanStop(rockVision,
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.PICK_UP, 88L,
                                "Advanced Medicines", 54)), 577)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, revised);

        javax.swing.JTable table = findTableWithColumn(panel, "Action");
        assertNotNull(table);
        assertEquals(false, table.getValueAt(1, 0));
        assertEquals(true, table.getValueAt(2, 0));
    }

    @Test
    void startingAManualUpdateAnnouncesReroutingAndRunsOneUpdate() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<String> spoken = new AtomicReference<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportRoutePlan existing = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, existing);
        writeField(panel, "manualRerouteAction", (Runnable) updates::incrementAndGet);
        writeField(panel, "planStatusSpeaker", (Consumer<String>) spoken::set);

        invoke(panel, "beginManualReroute", new Class<?>[0]);

        assertEquals(1, updates.get());
        assertEquals("Rerouting...", spoken.get());
        assertNotNull(findLabelContaining(panel, "Rerouting"));
    }

    @Test
    void successfulManualRerouteAppliesTheRouteAndAnnouncesCompletion() {
        List<List<String>> applied = new ArrayList<>();
        AtomicReference<String> spoken = new AtomicReference<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, applied::add);
        writeField(panel, "planStatusSpeaker", (Consumer<String>) spoken::set);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 10.0, true);

        invoke(panel, "completePreparedPlan",
                new Class<?>[] { TransportRoutePlan.class, TransportPlanRequest.class,
                        List.class, boolean.class },
                plan, null, List.of(), true);

        assertEquals(List.of(List.of("Sol", "Lave")), applied);
        assertEquals("Route updated.", spoken.get());
        assertNotNull(findLabelContaining(panel, "Plan current"));
    }

    @Test
    void completedPlanOpensPlanTabAndInvalidationLeavesTheEmptyPlanAvailable() {
        List<List<String>> applied = new ArrayList<>();
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, applied::add);
        TransportLocation lave = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(lave, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8)), 8)), 10.0, true);

        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        JButton optimizedPlan = findButton(panel, "Optimized Plan");
        assertTrue(optimizedPlan.isEnabled());
        assertNotNull(findButton(panel, "Update Plan"));
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
        JButton apply = findButton(panel, "Apply to Route");
        assertNotNull(apply);
        apply.doClick();
        assertEquals(List.of(List.of("Sol", "Lave")), applied);

        invoke(panel, "invalidateOptimizedPlan", new Class<?>[0]);

        assertTrue(optimizedPlan.isEnabled());
        assertFalse(findNamed(panel, "optimizedPlanContent").isVisible());
        assertTrue(findNamed(panel, "allMissionsContent").isVisible());
        optimizedPlan.doClick();
        assertNotNull(findLabelContaining(panel, "No transport plan yet"));
    }

    @Test
    void changingCurrentLocationKeepsTheActivePlan() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation lave = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(lave, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        panel.handleLogEvent(new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false, "Achenar", 2L, new double[] { 20, 0, 0 }, null, 0, null));

        assertTrue(findButton(panel, "Optimized Plan").isEnabled());
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
    }

    @Test
    void enteringSystemHighlightsPlanFromEventBeforeSharedLocationCatchesUp() throws Exception {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation achenar = new TransportLocation("Achenar", "Dawes Hub", 20, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(achenar, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0)), 20.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        panel.handleLogEvent(new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false, "Achenar", 2L, new double[] { 20, 0, 0 }, null, 0, null));
        SwingUtilities.invokeAndWait(() -> { });

        Object planPanel = readField(panel, "optimizedPlanPanel");
        Method highlighted = planPanel.getClass().getDeclaredMethod("highlightedRowForTests");
        highlighted.setAccessible(true);
        assertEquals(1, highlighted.invoke(planPanel));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, highlighted.invoke(planPanel));
    }

    @Test
    void fsdJumpAdvancesHighlightFromPickupToNextDeliverySystem() throws Exception {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Start", () -> "Start Port",
                () -> 128, systems -> { });
        TransportPlanAction visit = new TransportPlanAction(
                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Source", "Market", 10, 0, 0), List.of(visit), 0),
                new TransportPlanStop(new TransportLocation("Gliese 868", "MacLean Terminal", 20, 0, 0),
                        List.of(visit), 0)), 20.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);
        panel.handleLogEvent(new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false, "Source", 2L, new double[] { 10, 0, 0 }, null, 0, null));
        SwingUtilities.invokeAndWait(() -> { });

        panel.handleLogEvent(new FsdJumpEvent(Instant.now(), new JsonObject(), "Gliese 868", 3L,
                new double[] { 20, 0, 0 }, "Gliese 868", 0, "Star", 10, 1, 10, false));
        SwingUtilities.invokeAndWait(() -> { });

        Object planPanel = readField(panel, "optimizedPlanPanel");
        Method highlighted = planPanel.getClass().getDeclaredMethod("highlightedRowForTests");
        highlighted.setAccessible(true);
        assertEquals(2, highlighted.invoke(planPanel));
    }

    @Test
    void cargoPurchaseKeepsTheActivePlan() throws Exception {
        JsonObject beforePurchase = new JsonObject();
        beforePurchase.add("Inventory", new com.google.gson.JsonArray());
        CargoMonitor.getInstance().setDebugSnapshot(beforePurchase);
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8)), 8)),
                10.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        Thread.sleep(2L);
        JsonObject afterPurchase = new JsonObject();
        com.google.gson.JsonArray inventory = new com.google.gson.JsonArray();
        JsonObject gold = new JsonObject();
        gold.addProperty("Name_Localised", "Gold");
        gold.addProperty("Count", 8);
        inventory.add(gold);
        afterPurchase.add("Inventory", inventory);
        CargoMonitor.getInstance().setDebugSnapshot(afterPurchase);

        assertTrue(findButton(panel, "Optimized Plan").isEnabled());
        assertTrue(findNamed(panel, "optimizedPlanContent").isVisible());
    }

    @Test
    void cargoDepotProgressKeepsTheActivePlan() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Gliese 868", () -> "MacLean Terminal",
                () -> 1056, systems -> { });
        EliteLogParser parser = new EliteLogParser();
        panel.handleLogEvent(parser.parseRecord("""
                {"timestamp":"2026-08-17T15:50:00Z","event":"MissionAccepted",
                 "Name":"Mission_Delivery","Commodity_Localised":"Haematite","Count":196,
                 "DestinationSystem":"Lave","DestinationStation":"Lave Station","MissionID":42}
                """));
        TransportLocation depot = new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(depot, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.PICK_UP, 42L, "Haematite", 196)), 196)), 0.0, true);
        invoke(panel, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);

        panel.handleLogEvent(parser.parseRecord("""
                {"timestamp":"2026-08-17T15:55:00Z","event":"CargoDepot","MissionID":42,
                 "UpdateType":"Collect","CargoType":"Haematite","Count":196,
                 "ItemsCollected":196,"ItemsDelivered":0,"TotalItemsToDeliver":196}
                """));

        assertTrue(findButton(panel, "Optimized Plan").isEnabled());
        assertNotNull(findButton(panel, "Update Plan"));
    }

    @Test
    void optimizedPlanDisplaysMissingPickupWarningsInsideTheTab() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation achenar = new TransportLocation("Achenar", "Dawes Hub", 20, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(achenar, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.VISIT, 5L, "Mining", 0)), 0)), 20.0, true);
        List<TransportPlanProblem> warnings = List.of(new TransportPlanProblem(
                TransportPlanProblem.Code.SOURCE_REQUIRED, 5L,
                "Pickup not planned: 12 t Bromellite source has not been set."));

        invoke(panel, "displayOptimizedPlan",
                new Class<?>[] { TransportRoutePlan.class, List.class }, plan, warnings);

        assertNotNull(findLabelContaining(panel, "Pickup not planned"));
        assertNotNull(findLabelContaining(panel, "Bromellite"));
    }

    @Test
    void lastOptimizedPlanRestoresFromSessionWithoutOpeningThePlanTab() {
        MissionsTabPanel saved = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        TransportLocation start = new TransportLocation("Sol", "Galileo", 0, 0, 0);
        TransportLocation lave = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(lave, List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8)), 20)), 10.0, true);
        TransportPlanRequest request = new TransportPlanRequest(start, 128, 12, List.of());
        List<TransportPlanProblem> warnings = List.of(new TransportPlanProblem(
                TransportPlanProblem.Code.SOURCE_REQUIRED, 2L,
                "Pickup not planned: source has not been set."));
        invoke(saved, "displayOptimizedPlan",
                new Class<?>[] { TransportRoutePlan.class, TransportPlanRequest.class, List.class },
                plan, request, warnings);
        EdoSessionState state = new EdoSessionState();
        saved.fillSessionState(state);
        state = new Gson().fromJson(new Gson().toJson(state), EdoSessionState.class);

        MissionsTabPanel restored = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });
        restored.applySessionState(state);

        JButton planTab = findButton(restored, "Optimized Plan");
        assertTrue(planTab.isEnabled());
        assertTrue(findNamed(restored, "allMissionsContent").isVisible());
        planTab.doClick();
        assertTrue(findNamed(restored, "optimizedPlanContent").isVisible());
        assertNotNull(findLabelContaining(restored, "source has not been set"));
        javax.swing.JTable table = findTableWithColumn(restored, "Action");
        assertNotNull(table);
        assertEquals("Sol", table.getValueAt(0, 2));
        assertEquals("Galileo", table.getValueAt(0, 3));
        assertEquals("12 / 128 t\nFree 116 t", table.getValueAt(0, 5));
        assertEquals("Lave", table.getValueAt(1, 2));
        assertEquals("Lave Station", table.getValueAt(1, 3));
        assertEquals("Pick up 8 t Gold", table.getValueAt(1, 4));
    }

    @Test
    void outOfOrderCompletionSurvivesRestartWithoutCheckingSkippedStop() {
        AtomicReference<String> system = new AtomicReference<>("Core Sys Sector AQ-P a5-1");
        AtomicReference<String> station = new AtomicReference<>("Rock Vision");
        MissionsTabPanel saved = new MissionsTabPanel(
                () -> false, () -> false, system::get, station::get,
                () -> 128, systems -> { });
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation(
                        "Gliese 868", "MacLean Terminal", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 10L, "Courier", 0)), 0),
                new TransportPlanStop(new TransportLocation(
                        "Gliese 868", "Braun Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 20L, "Courier", 0)), 0)),
                10.0, true);
        invoke(saved, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);
        system.set("Gliese 868");
        station.set("Braun Station");
        invoke(saved, "updateOptimizedPlanLocationHighlight", new Class<?>[0]);
        saved.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:00:00Z","event":"MissionCompleted",
                 "MissionID":20}
                """));

        EdoSessionState state = new EdoSessionState();
        saved.fillSessionState(state);
        state = new Gson().fromJson(new Gson().toJson(state), EdoSessionState.class);
        MissionsTabPanel restored = new MissionsTabPanel(
                () -> false, () -> false, system::get, station::get,
                () -> 128, systems -> { });
        restored.applySessionState(state);
        findButton(restored, "Optimized Plan").doClick();

        javax.swing.JTable table = findTableWithColumn(restored, "Action");
        assertNotNull(table);
        assertEquals(false, table.getValueAt(1, 0));
        assertEquals(true, table.getValueAt(2, 0));
    }

    @Test
    void completedStopCheckSurvivesSessionRoundTrip() {
        AtomicReference<String> system = new AtomicReference<>("Sol");
        AtomicReference<String> station = new AtomicReference<>("Galileo");
        MissionsTabPanel saved = new MissionsTabPanel(
                () -> false, () -> false, system::get, station::get,
                () -> 128, systems -> { });
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0)), 0),
                new TransportPlanStop(new TransportLocation("Leesti", "George Lucas", 20, 0, 0),
                        List.of(new TransportPlanAction(
                                TransportPlanAction.Kind.VISIT, 2L, "Courier", 0)), 0)), 20.0, true);
        invoke(saved, "displayOptimizedPlan", new Class<?>[] { TransportRoutePlan.class }, plan);
        system.set("Lave");
        station.set("Lave Station");
        invoke(saved, "updateOptimizedPlanLocationHighlight", new Class<?>[0]);
        saved.handleLogEvent(new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-18T01:05:00Z","event":"MissionCompleted",
                 "MissionID":1}
                """));
        system.set("Leesti");
        station.set("George Lucas");
        invoke(saved, "updateOptimizedPlanLocationHighlight", new Class<?>[0]);
        EdoSessionState state = new EdoSessionState();
        saved.fillSessionState(state);
        state = new Gson().fromJson(new Gson().toJson(state), EdoSessionState.class);

        MissionsTabPanel restored = new MissionsTabPanel(
                () -> false, () -> false, system::get, station::get,
                () -> 128, systems -> { });
        restored.applySessionState(state);
        findButton(restored, "Optimized Plan").doClick();

        javax.swing.JTable table = findTableWithColumn(restored, "Action");
        assertNotNull(table);
        assertEquals(true, table.getValueAt(1, 0));
        assertEquals(1, table.getValueAt(1, 1));
        assertEquals(false, table.getValueAt(2, 0));
        assertEquals(2, table.getValueAt(2, 1));
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JComponent findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component && name.equals(component.getName())) return component;
            if (child instanceof Container container) {
                JComponent found = findNamed(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JLabel findLabelContaining(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null
                    && label.getText().contains(text)) return label;
            if (child instanceof Container container) {
                JLabel found = findLabelContaining(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static javax.swing.JTable findTableWithColumn(Container root, String column) {
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JTable table) {
                for (int i = 0; i < table.getColumnCount(); i++) {
                    if (column.equals(table.getColumnName(i))) return table;
                }
            }
            if (child instanceof Container container) {
                javax.swing.JTable found = findTableWithColumn(container, column);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            fail("Expected UI behavior method " + name, ex);
        }
    }

    private static Object readField(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            fail("Expected UI state field " + name, ex);
            return null;
        }
    }

    private static void writeField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            fail("Expected UI state field " + name, ex);
        }
    }
}
