package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;

import org.dce.ed.logreader.EliteLogEvent.NavRouteClearEvent;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteMarkerKind;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionMapper;
import org.dce.ed.state.SystemState;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * Unit tests for route helper methods: findSystemRow, deepCopy, bestInsertionIndexByCoords, recomputeLegDistances, renumberDisplayIndexes.
 */
class RouteTabPanelHelperTest {

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
        panel.setCustomRouteActive(true);

        panel.handleLogEvent(new NavRouteClearEvent(Instant.parse("2026-01-01T00:00:00Z"), new JsonObject()));

        assertTrue(panel.isCustomRouteActive());
        assertEquals(3, panel.routeSessionForTests().getBaseRouteEntries().size());
        assertEquals("Achenar", panel.routeSessionForTests().getBaseRouteEntries().get(1).systemName);
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
        panel.routeSessionForTests().replaceBaseRouteEntries(List.of(
                entry("Core Sys Sector CB-O a6-1", 100L),
                entry("Gliese 868", 200L),
                entry("Core Sys Sector CB-O a6-1", 100L),
                entry("Gliese 868", 200L)));
        // Journal FSDJump already advanced the route session.
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
