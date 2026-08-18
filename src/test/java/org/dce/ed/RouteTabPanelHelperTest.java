package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
import org.dce.ed.exec.placeholder.ExecPlaceholderId;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;
import org.dce.ed.edsm.SystemResponse;
import org.dce.ed.logreader.EliteLogEvent.NavRouteEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteClearEvent;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.FuelScoopStarClass;
import org.dce.ed.route.RouteMarkerKind;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionMapper;
import org.dce.ed.state.SystemState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/**
 * Unit tests for route helper methods: findSystemRow, deepCopy, bestInsertionIndexByCoords, recomputeLegDistances, renumberDisplayIndexes.
 */
class RouteTabPanelHelperTest {
	@Test
	void transportPlanningUsesLiveRouteSessionSystem() {
		RouteTabPanel panel = new RouteTabPanel(() -> false);
		panel.routeSessionForTests().applyKnownCurrentSystem(
				"Col 285 Sector MK-P a35-0", 123L, new double[] { 1, 2, 3 });

		assertEquals("Col 285 Sector MK-P a35-0", panel.getCurrentSystemNameForPlanning());
	}

    @Test
    void customRouteLoopToggle_isBeforeClearAndPersistsSelection() {
        boolean saved = OverlayPreferences.isCustomRouteLoopEnabled();
        try {
            OverlayPreferences.setCustomRouteLoopEnabled(false);
            RouteTabPanel panel = new RouteTabPanel();
            JPanel strip = panel.customRouteWarningStripForTests();
            assertEquals("Loop", panel.loopButtonForTests().getToolTipText());
            assertEquals(panel.loopButtonForTests(), strip.getComponent(1));
            assertEquals(panel.clearButtonForTests(), strip.getComponent(2));

            panel.loopButtonForTests().doClick();

            assertTrue(panel.loopButtonForTests().isSelected());
            assertTrue(OverlayPreferences.isCustomRouteLoopEnabled());
        } finally {
            OverlayPreferences.setCustomRouteLoopEnabled(saved);
        }
    }

    @Test
    void activatingCustomRouteMakesRememberedLoopStateAvailableToExecVariable() throws Exception {
        boolean saved = OverlayPreferences.isCustomRouteLoopEnabled();
        try {
            OverlayPreferences.setCustomRouteLoopEnabled(true);
            SwingUtilities.invokeAndWait(() -> {
                RouteTabPanel panel = new RouteTabPanel();
                panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                        entry("Alpha", 100L),
                        entry("Beta", 200L)));
                panel.onCustomRouteMutated();
                panel.routeSessionForTests().applyKnownCurrentSystem("Alpha", 100L, null);
                panel.routeSessionForTests().applyKnownCurrentSystem("Beta", 200L, null);

                SystemState live = new SystemState();
                live.setSystemName("Beta");
                live.setSystemAddress(200L);
                ExecPlaceholderContext ctx = new ExecPlaceholderContext();
                ctx.setShipRouteSessionSupplier(panel::getRouteSession);
                ctx.setSystemStateSupplier(() -> live);

                assertEquals("Alpha", ExecPlaceholderResolver.resolveOne(
                        ctx, null, ExecPlaceholderId.ROUTE_NEXT_DESTINATION));
            });
            SwingUtilities.invokeAndWait(() -> { });
        } finally {
            OverlayPreferences.setCustomRouteLoopEnabled(saved);
        }
    }

    @Test
    void findSystemRow_emptyList_returnsMinusOne() {
        List<RouteEntry> entries = new ArrayList<>();
        assertEquals(-1, RouteTabPanel.findSystemRow(entries, "Sol", 100L));
    }

    @Test
    void findSystemRow_matchByAddress_returnsIndex() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("Sol", 100L));
        entries.add(entry("Alpha Centauri", 200L));
        assertEquals(0, RouteTabPanel.findSystemRow(entries, "Sol", 100L));
        assertEquals(1, RouteTabPanel.findSystemRow(entries, "Alpha Centauri", 200L));
    }

    @Test
    void findSystemRow_matchByName_returnsIndex() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("Sol", 100L));
        assertEquals(0, RouteTabPanel.findSystemRow(entries, "Sol", 0L));
    }

    @Test
    void findSystemRow_skipsBodyRows() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("Sol", 100L));
        RouteEntry body = RouteEntry.syntheticBody("Earth");
        body.isBodyRow = true;
        entries.add(body);
        entries.add(entry("Alpha Centauri", 200L));
        assertEquals(2, RouteTabPanel.findSystemRow(entries, "Alpha Centauri", 200L));
    }

    @Test
    void deepCopy_copiesEntriesIndependently() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("Sol", 100L));
        List<RouteEntry> copy = RouteTabPanel.deepCopy(entries);
        assertEquals(1, copy.size());
        assertEquals("Sol", copy.get(0).systemName);
        assertEquals(100L, copy.get(0).systemAddress);
        copy.get(0).systemName = "Other";
        assertEquals("Sol", entries.get(0).systemName);
    }

    @Test
    void recomputeLegDistances_setsFirstToNull_restToDistance() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entryWithCoords("A", 0, 0, 0));
        entries.add(entryWithCoords("B", 1, 0, 0));
        entries.add(entryWithCoords("C", 1, 1, 0));
        RouteTabPanel.recomputeLegDistances(entries);
        assertNull(entries.get(0).distanceLy);
        assertEquals(1.0, entries.get(1).distanceLy, 1e-6);
        assertEquals(1.0, entries.get(2).distanceLy, 1e-6);
    }

    @Test
    void customRouteEdsmMetadataPopulatesFuelClassAndLegDistanceInputs() {
        RouteEntry entry = entry("Lave", 0L);
        SystemResponse response = new SystemResponse();
        response.name = "Lave";
        response.id64 = 123L;
        response.coords = new SystemResponse.Coordinates();
        response.coords.x = 10.0;
        response.coords.y = 20.0;
        response.coords.z = 30.0;
        response.primaryStar = new SystemResponse.PrimaryStar();
        response.primaryStar.type = "K";

        RouteTabPanel.applyEdsmSystemMetadata(entry, response);

        assertEquals(123L, entry.systemAddress);
        assertEquals("K", entry.starClass);
        assertEquals(10.0, entry.x);
        assertEquals(20.0, entry.y);
        assertEquals(30.0, entry.z);
        assertTrue(FuelScoopStarClass.isFuelScoopable(entry.starClass));
    }

    @Test
    void confirmedOptimizedRouteReplacesCustomStopsAndKeepsCurrentSystemFirst() {
        RouteTabPanel panel = new RouteTabPanel(() -> false);
        panel.routeSessionForTests().applyKnownCurrentSystem("Stale System", 1L, new double[] { -5, 0, 0 });
        RouteEntry sol = entryWithCoords("Sol", 0, 0, 0);
        sol.systemAddress = 2L;
        RouteEntry lave = entryWithCoords("Lave", 10, 0, 0);
        lave.starClass = "K";
        RouteEntry achenar = entryWithCoords("Achenar", 30, 0, 0);
        achenar.starClass = "G";

        panel.applyResolvedOptimizedRoute(List.of(sol, lave, achenar));

        assertTrue(panel.isCustomRouteActive());
        assertEquals(List.of("Sol", "Lave", "Achenar"),
                panel.routeSessionForTests().getBaseRouteEntries().stream()
                        .map(e -> e.systemName).toList());
        assertEquals("Sol", panel.routeSessionForTests().getCurrentSystemName());
        List<RouteEntry> displayed = panel.routeSessionForTests()
                .buildDisplaySnapshot(null, (n, a, p) -> null, true).displayedEntries();
        assertEquals(10.0, displayed.get(1).distanceLy, 0.0001);
        assertEquals(20.0, displayed.get(2).distanceLy, 0.0001);
    }

    @Test
    void renumberDisplayIndexes_startsAtZeroAndSkipsBodyRows() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("A", 1L));
        entries.add(RouteEntry.syntheticBody("A Station"));
        entries.add(entry("B", 2L));
        RouteTabPanel.renumberDisplayIndexes(entries);
        assertEquals(Integer.valueOf(0), entries.get(0).displayIndex);
        assertNull(entries.get(1).displayIndex);
        assertEquals(Integer.valueOf(1), entries.get(2).displayIndex);
    }

    @Test
    void sessionRestore_displaysRouteAsJumpCount() {
        RouteTabPanel panel = new StatusFirstRouteTabPanel();
        EdoSessionState state = new EdoSessionState();
        state.setCustomRouteActive(Boolean.TRUE);
        state.setCustomRouteEntries(List.of(
                FleetCarrierSessionMapper.toPersisted(entry("Sol", 1L)),
                FleetCarrierSessionMapper.toPersisted(entry("Achenar", 2L)),
                FleetCarrierSessionMapper.toPersisted(entry("Shinrarta Dezhra", 3L))));

        panel.applySessionState(state);

        assertEquals("Route: 2 jumps", findRouteHeader(panel));
    }

    @Test
    void clearCustomRoute_withEmptyGamePlot_seedsCurrentSystemAndClearsLatch() {
        EmptyNavRouteClearPanel panel = new EmptyNavRouteClearPanel();
        panel.routeSessionForTests().applyKnownCurrentSystem("Sol", 1L, null);
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Sol", 1L),
                entry("Achenar", 2L)));
        panel.setCustomRouteActive(true);

        panel.clearCustomRoute();

        assertEquals(false, panel.isCustomRouteActive());
        assertEquals(1, panel.routeSessionForTests().getBaseRouteEntries().size());
        assertEquals("Sol", panel.routeSessionForTests().getBaseRouteEntries().get(0).systemName);
        assertEquals(1L, panel.routeSessionForTests().getBaseRouteEntries().get(0).systemAddress);
    }

    @Test
    void navRouteClear_whileCustomRouteActive_keepsCustomHops() {
        RouteTabPanel panel = new RouteTabPanel(() -> false);
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Sol", 1L),
                entry("Achenar", 2L),
                entry("Shinrarta Dezhra", 3L)));
        panel.routeSessionForTests().applyKnownCurrentSystem("Sol", 1L, null);
        panel.routeSessionForTests().replaceCustomNavRouteEntries(List.of(
                entry("Sol", 1L),
                entry("Generated Midpoint", 99L),
                entry("Achenar", 2L)));
        panel.setCustomRouteActive(true);

        panel.handleLogEvent(new NavRouteClearEvent(Instant.parse("2026-01-01T00:00:00Z"), new JsonObject()));

        assertTrue(panel.isCustomRouteActive());
        assertEquals(3, panel.routeSessionForTests().getBaseRouteEntries().size());
        assertEquals("Achenar", panel.routeSessionForTests().getBaseRouteEntries().get(1).systemName);
        assertTrue(panel.routeSessionForTests()
                .buildDisplaySnapshot(null, (n, a, p) -> null, true)
                .displayedEntries().stream()
                .noneMatch(e -> "Generated Midpoint".equals(e.systemName)));
    }

    @Test
    void navRouteToCustomDestination_displaysEveryGeneratedIntermediate(@TempDir Path journalDir)
            throws Exception {
        String savedClientKey = EliteDangerousOverlay.clientKey;
        String clientKey = "route-custom-intermediates-test";
        boolean savedAuto = OverlayPreferences.isAutoLogDir(clientKey);
        String savedCustom = OverlayPreferences.getCustomLogDir(clientKey);
        Files.writeString(journalDir.resolve("Journal.2026-08-15T200000.01.log"), "");
        Files.writeString(journalDir.resolve("NavRoute.json"), """
                {"Route":[
                  {"StarSystem":"Gliese 868","SystemAddress":1,"StarClass":"K","StarPos":[0,0,0]},
                  {"StarSystem":"LTT 569","SystemAddress":2,"StarClass":"M","StarPos":[10,0,0]},
                  {"StarSystem":"Arietis Sector ZE-A d89","SystemAddress":3,"StarClass":"K","StarPos":[20,0,0]},
                  {"StarSystem":"Arietis Sector CO-P b5-1","SystemAddress":4,"StarClass":"F","StarPos":[30,0,0]}
                ]}
                """);

        try {
            EliteDangerousOverlay.clientKey = clientKey;
            OverlayPreferences.setAutoLogDir(clientKey, false);
            OverlayPreferences.setCustomLogDir(clientKey, journalDir.toString());
            RouteTabPanel panel = new RouteTabPanel(() -> false);
            panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                    entry("Gliese 868", 1L),
                    entry("Arietis Sector CO-P b5-1", 4L),
                    entry("Col 285 Sector CC-J b23-3", 5L)));
            panel.routeSessionForTests().applyKnownCurrentSystem("Gliese 868", 1L, null);
            panel.setCustomRouteActive(true);

            panel.handleLogEvent(new NavRouteEvent(
                    Instant.parse("2026-08-15T20:00:00Z"), new JsonObject()));

            List<RouteEntry> displayed = panel.routeSessionForTests()
                    .buildDisplaySnapshot(null, (n, a, p) -> null, true)
                    .displayedEntries();
            assertEquals(List.of(
                    "Gliese 868",
                    "LTT 569",
                    "Arietis Sector ZE-A d89",
                    "Arietis Sector CO-P b5-1",
                    "Col 285 Sector CC-J b23-3"),
                    displayed.stream().map(e -> e.systemName).toList());
            assertTrue(displayed.get(1).isSynthetic);
            assertNull(displayed.get(1).displayIndex);
            assertTrue(displayed.get(2).isSynthetic);
            assertNull(displayed.get(2).displayIndex);
            assertEquals(Integer.valueOf(1), displayed.get(3).displayIndex);
            assertEquals(3, panel.routeSessionForTests().getBaseRouteEntries().size());

            Files.writeString(journalDir.resolve("NavRoute.json"), """
                    {"Route":[
                      {"StarSystem":"Gliese 868","SystemAddress":1,"StarClass":"K","StarPos":[0,0,0]},
                      {"StarSystem":"HIP 22550","SystemAddress":6,"StarClass":"M","StarPos":[15,0,0]},
                      {"StarSystem":"Arietis Sector CO-P b5-1","SystemAddress":4,"StarClass":"F","StarPos":[30,0,0]}
                    ]}
                    """);
            panel.handleLogEvent(new NavRouteEvent(
                    Instant.parse("2026-08-15T20:01:00Z"), new JsonObject()));

            displayed = panel.routeSessionForTests()
                    .buildDisplaySnapshot(null, (n, a, p) -> null, true)
                    .displayedEntries();
            assertEquals(List.of(
                    "Gliese 868",
                    "HIP 22550",
                    "Arietis Sector CO-P b5-1",
                    "Col 285 Sector CC-J b23-3"),
                    displayed.stream().map(e -> e.systemName).toList());
        } finally {
            EliteDangerousOverlay.clientKey = savedClientKey;
            OverlayPreferences.setAutoLogDir(clientKey, savedAuto);
            OverlayPreferences.setCustomLogDir(clientKey, savedCustom != null ? savedCustom : "");
        }
    }

    @Test
    void parsePastedSystemNames_splitsLinesAndCommas() {
        List<String> names = RouteTabPanel.parsePastedSystemNames("Sol\nAlpha Centauri, Barnard's Star");
        assertEquals(List.of("Sol", "Alpha Centauri", "Barnard's Star"), names);
    }

    @Test
    void parsePastedSystemNames_skipsBlankAndHeader() {
        List<String> names = RouteTabPanel.parsePastedSystemNames("System\n\n  Sol  \n");
        assertEquals(List.of("Sol"), names);
    }

    @Test
    void bestInsertionIndexByCoords_emptyList_returnsZero() {
        assertEquals(0, RouteTabPanel.bestInsertionIndexByCoords(new ArrayList<>(), new Double[]{1.0, 2.0, 3.0}));
    }

    @Test
    void bestInsertionIndexByCoords_nullCoords_returnsEnd() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entryWithCoords("A", 0, 0, 0));
        assertEquals(1, RouteTabPanel.bestInsertionIndexByCoords(entries, null));
    }

    @Test
    void bestInsertionIndexByCoords_pointBetweenTwoSystems_returnsOne() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entryWithCoords("A", 0, 0, 0));
        entries.add(entryWithCoords("B", 2, 0, 0));
        // Point (1,0,0) is on the segment A->B, so insert after index 0
        int idx = RouteTabPanel.bestInsertionIndexByCoords(entries, new Double[]{1.0, 0.0, 0.0});
        assertEquals(1, idx);
    }

    @Test
    void startupReconcile_prefersLatestJournalLocationOverPersistedCurrent() {
        JournalFirstRouteTabPanel panel = new JournalFirstRouteTabPanel();
        panel.routeSessionForTests().applyKnownCurrentSystem("47 Ursae Majoris", 47L, null);

        panel.reconcileRouteCurrentWithPostRescanCache();

        assertEquals("Ross 104", panel.routeSessionForTests().getCurrentSystemName());
        assertEquals(104L, panel.routeSessionForTests().getCurrentSystemAddress());
    }

    @Test
    void sessionRestore_reconcilesPersistedDestinationWithCurrentStatusSnapshot() {
        StatusFirstRouteTabPanel panel = new StatusFirstRouteTabPanel();
        EdoSessionState state = new EdoSessionState();
        state.setDestinationSystemAddress(104L);
        state.setDestinationBodyId(7);
        state.setDestinationName("Bunch City");

        panel.applySessionState(state);

        assertEquals(true, panel.statusSnapshotApplied);
        assertNull(panel.routeSessionForTests().getTargetState().getDestinationName());
    }

    @Test
    void rebuild_adoptsLiveSystemStateWhenSessionCurrentLagsOneHop() {
        RouteTabPanel panel = new RouteTabPanel(() -> false) {
            @Override
            protected boolean resolveCurrentSystemFromJournal() {
                return false;
            }
        };
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Kuan Ti", 100L),
                entry("Cemiess", 200L),
                entry("Achenar", 300L)));
        panel.routeSessionForTests().applyKnownCurrentSystem("Kuan Ti", 100L, null);

        SystemState live = new SystemState();
        live.setSystemName("Cemiess");
        live.setSystemAddress(200L);
        panel.setLiveSystemStateSupplier(() -> live);

        panel.rebuildDisplayedEntries();

        assertEquals("Cemiess", panel.routeSessionForTests().getCurrentSystemName());
        assertEquals(200L, panel.routeSessionForTests().getCurrentSystemAddress());
        var displayed = panel.routeSessionForTests().buildDisplaySnapshot(null, (n, a, p) -> null, false)
                .displayedEntries();
        assertEquals(RouteMarkerKind.CURRENT, displayed.get(1).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, displayed.get(2).markerKind);
    }

    @Test
    void rebuild_doesNotRegressCursorWhenLiveSystemStateLagsJournalArrival() {
        RouteTabPanel panel = new RouteTabPanel(() -> false) {
            @Override
            protected boolean resolveCurrentSystemFromJournal() {
                return false;
            }
        };
        // Non-looping route: previous hop must not reappear later, or a lagging SystemState
        // would look identical to arriving at a future revisit.
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Core Sys Sector CB-O a6-1", 100L),
                entry("Gliese 868", 200L),
                entry("Sol", 300L)));
        panel.routeSessionForTests().applyKnownCurrentSystem("Core Sys Sector CB-O a6-1", 100L, null);
        panel.routeSessionForTests().applyKnownCurrentSystem("Gliese 868", 200L, null);
        assertEquals(1, panel.routeSessionForTests().getCurrentBaseIndex());

        // System tab still briefly reports the previous hop.
        SystemState live = new SystemState();
        live.setSystemName("Core Sys Sector CB-O a6-1");
        live.setSystemAddress(100L);
        panel.setLiveSystemStateSupplier(() -> live);

        panel.rebuildDisplayedEntries();

        assertEquals("Gliese 868", panel.routeSessionForTests().getCurrentSystemName());
        assertEquals(1, panel.routeSessionForTests().getCurrentBaseIndex());
        var displayed = panel.routeSessionForTests().buildDisplaySnapshot(null, (n, a, p) -> null, false)
                .displayedEntries();
        assertEquals(RouteMarkerKind.CURRENT, displayed.get(1).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, displayed.get(2).markerKind);
    }

    @Test
    void rebuild_advancesLoopCursorWhenLiveArrivesAtRevisitedSystem() {
        RouteTabPanel panel = new RouteTabPanel(() -> false) {
            @Override
            protected boolean resolveCurrentSystemFromJournal() {
                return false;
            }
        };
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Gliese 868", 200L),
                entry("Core Sys Sector CB-O a6-1", 100L),
                entry("Gliese 868", 200L),
                entry("Core Sys Sector CB-O a6-1", 100L)));
        panel.routeSessionForTests().applyKnownCurrentSystem("Gliese 868", 200L, null);
        panel.routeSessionForTests().applyKnownCurrentSystem("Core Sys Sector CB-O a6-1", 100L, null);
        assertEquals(1, panel.routeSessionForTests().getCurrentBaseIndex());

        // Arrived back in Gliese — SystemState is ahead of a sticky route session.
        SystemState live = new SystemState();
        live.setSystemName("Gliese 868");
        live.setSystemAddress(200L);
        panel.setLiveSystemStateSupplier(() -> live);

        panel.rebuildDisplayedEntries();

        assertEquals("Gliese 868", panel.routeSessionForTests().getCurrentSystemName());
        assertEquals(2, panel.routeSessionForTests().getCurrentBaseIndex());
        var displayed = panel.routeSessionForTests().buildDisplaySnapshot(null, (n, a, p) -> null, false)
                .displayedEntries();
        assertEquals(RouteMarkerKind.NONE, displayed.get(0).markerKind);
        assertEquals(RouteMarkerKind.NONE, displayed.get(1).markerKind);
        assertEquals(RouteMarkerKind.CURRENT, displayed.get(2).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, displayed.get(3).markerKind);
    }

    @Test
    void rebuild_resyncsLoopCursorWhenNameMatchesLiveButIndexStuck() throws Exception {
        RouteTabPanel panel = new RouteTabPanel(() -> false) {
            @Override
            protected boolean resolveCurrentSystemFromJournal() {
                return false;
            }
        };
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Gliese 868", 200L),
                entry("Core Sys Sector CB-O a6-1", 100L),
                entry("Gliese 868", 200L),
                entry("Core Sys Sector CB-O a6-1", 100L)));
        panel.routeSessionForTests().applyKnownCurrentSystem("Gliese 868", 200L, null);
        panel.routeSessionForTests().applyKnownCurrentSystem("Core Sys Sector CB-O a6-1", 100L, null);
        assertEquals(1, panel.routeSessionForTests().getCurrentBaseIndex());

        // Simulate desync: identity already says Gliese but cursor still on Core.
        var session = panel.routeSessionForTests();
        session.applyKnownCurrentSystem("Gliese 868", 200L, null);
        assertEquals(2, session.getCurrentBaseIndex());
        var indexField = session.getClass().getDeclaredField("currentBaseIndex");
        indexField.setAccessible(true);
        indexField.setInt(session, 1);
        assertEquals(1, session.getCurrentBaseIndex());
        assertEquals("Gliese 868", session.getCurrentSystemName());

        SystemState live = new SystemState();
        live.setSystemName("Gliese 868");
        live.setSystemAddress(200L);
        panel.setLiveSystemStateSupplier(() -> live);

        panel.rebuildDisplayedEntries();

        assertEquals(2, panel.routeSessionForTests().getCurrentBaseIndex());
        var displayed = panel.routeSessionForTests().buildDisplaySnapshot(null, (n, a, p) -> null, false)
                .displayedEntries();
        var currentLoopHop = displayed.stream()
                .filter(e -> e != null && !e.isSynthetic && !e.isBodyRow && e.index == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(RouteMarkerKind.CURRENT, currentLoopHop.markerKind);
    }

    private static final class JournalFirstRouteTabPanel extends RouteTabPanel {
        @Override
        protected boolean resolveCurrentSystemFromJournal() {
            routeSessionForTests().applyKnownCurrentSystem("Ross 104", 104L, null);
            return true;
        }
    }

    /**
     * Stubs the NavRoute reload path so Clear can be tested without touching a live journal dir.
     */
    private static final class EmptyNavRouteClearPanel extends RouteTabPanel {
        @Override
        protected void replaceCustomRouteFromGamePlot() {
            routeSessionForTests().applyNavRouteReloadParsed(List.of());
            setCustomRouteActive(false);
            rebuildDisplayedEntries();
        }
    }

    private static final class StatusFirstRouteTabPanel extends RouteTabPanel {
        boolean statusSnapshotApplied;

        @Override
        protected boolean resolveCurrentSystemFromJournal() {
            return false;
        }

        @Override
        protected void reconcileRouteDestinationWithStatusSnapshot() {
            statusSnapshotApplied = true;
            routeSessionForTests().getTargetState().restoreFromPersistence(
                    null, null, null, null, null);
        }
    }

    private static String findRouteHeader(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null
                    && label.getText().startsWith("Route:")) {
                return label.getText();
            }
            if (child instanceof Container container) {
                String found = findRouteHeader(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static RouteEntry entry(String systemName, long systemAddress) {
        RouteEntry e = new RouteEntry();
        e.systemName = systemName;
        e.systemAddress = systemAddress;
        e.isBodyRow = false;
        return e;
    }

    private static RouteEntry entryWithCoords(String systemName, double x, double y, double z) {
        RouteEntry e = entry(systemName, 0L);
        e.x = x;
        e.y = y;
        e.z = z;
        return e;
    }
}
