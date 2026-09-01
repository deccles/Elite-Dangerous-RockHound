package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RouteEdsmPrefetchTest {

    @Test
    void openingWindowIsBusyWhileAnyPendingSystemRemains() {
        List<RouteEntry> rows = routeRows(30);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        RouteEdsmWindow.apply(rows, RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE);

        assertTrue(RouteEdsmPrefetch.openingWindowBusy(rows));
        rows.get(0).status = RouteScanStatus.UNKNOWN;
        assertTrue(RouteEdsmPrefetch.openingWindowBusy(rows));
        for (int i = 0; i < RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE; i++) {
            rows.get(i).status = RouteScanStatus.UNKNOWN;
        }
        assertFalse(RouteEdsmPrefetch.openingWindowBusy(rows));
    }

    @Test
    void nextUnresolvedRowsIncludePendingThenDeferredAndSkipBodyRows() {
        List<RouteEntry> rows = routeRows(8);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        rows.get(0).status = RouteScanStatus.PENDING;
        rows.get(1).status = RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED;
        rows.get(2).status = RouteScanStatus.DEFERRED;
        rows.add(3, RouteEntry.syntheticBody("body"));
        rows.add(4, RouteEntry.syntheticSystem("detour", 999L, null, RouteMarkerKind.NONE));
        rows.get(5).status = RouteScanStatus.PENDING;

        Set<String> inFlight = Set.of("System 2");
        List<Integer> selected = RouteEdsmPrefetch.nextUnresolvedRowIndexes(rows, 8,
                entry -> inFlight.contains(entry.systemName));

        assertEquals(List.of(Integer.valueOf(0), Integer.valueOf(4), Integer.valueOf(5),
                Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8), Integer.valueOf(9)),
                selected);
    }

    @Test
    void statusFromBodiesMarksDeferredEmptyResultAsUnknown() {
        assertEquals(RouteScanStatus.UNKNOWN, RouteEdsmPrefetch.statusFromBodies(null, false));
        org.dce.ed.edsm.BodiesResponse empty = new org.dce.ed.edsm.BodiesResponse();
        empty.bodies = java.util.List.of();
        assertEquals(RouteScanStatus.UNKNOWN, RouteEdsmPrefetch.statusFromBodies(empty, false));
    }

    @Test
    void statusFromBodiesUsesReturnedBodyList() {
        org.dce.ed.edsm.BodiesResponse bodies = new org.dce.ed.edsm.BodiesResponse();
        bodies.bodyCount = 1;
        bodies.bodies = java.util.List.of(new org.dce.ed.edsm.BodiesResponse.Body());
        assertEquals(RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED,
                RouteEdsmPrefetch.statusFromBodies(bodies, false));
    }

    private static List<RouteEntry> routeRows(int count) {
        List<RouteEntry> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new RouteEntry(i, "System " + i, i + 1L, "K", i, RouteScanStatus.PENDING));
        }
        return rows;
    }
}
