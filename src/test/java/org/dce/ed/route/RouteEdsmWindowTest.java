package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteEdsmWindowTest {

    @Test
    void onlyCurrentAndNextWindowSystemRowsAreQueryable() {
        int window = RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE;
        List<RouteEntry> rows = routeRows(window + 5);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;

        RouteEdsmWindow.apply(rows, window);

        assertEquals(RouteScanStatus.PENDING, rows.get(window - 1).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(window).status);
        assertEquals(window, rows.stream().filter(row -> row.status.needsEdsmQuery()).count());
    }

    @Test
    void advancingCurrentPromotesOneNewRowWithoutChangingResolvedRowsBehind() {
        int window = RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE;
        List<RouteEntry> rows = routeRows(window + 5);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        RouteEdsmWindow.apply(rows, window);
        rows.get(0).status = RouteScanStatus.UNKNOWN;
        rows.get(0).markerKind = RouteMarkerKind.NONE;
        rows.get(1).markerKind = RouteMarkerKind.CURRENT;

        RouteEdsmWindow.apply(rows, window);

        assertEquals(RouteScanStatus.UNKNOWN, rows.get(0).status);
        assertEquals(RouteScanStatus.PENDING, rows.get(window).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(window + 1).status);
    }

    @Test
    void bodyRowsDoNotConsumeWindowSlots() {
        int window = RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE;
        List<RouteEntry> rows = routeRows(window + 1);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        rows.add(1, RouteEntry.syntheticBody("body"));

        RouteEdsmWindow.apply(rows, window);

        assertEquals(RouteScanStatus.PENDING, rows.get(window).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(window + 1).status);
    }

    @Test
    void customNavRouteIntermediatesConsumeWindowSlotsAndStayQueryable() {
        int window = RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE;
        List<RouteEntry> rows = routeRows(2);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        rows.add(1, RouteEntry.syntheticSystem("Eol Prou AP-U b18-9", 99L, null, RouteMarkerKind.NONE));
        rows.add(2, RouteEntry.syntheticSystem("Eol Prou TD-S d4-530", 100L, null, RouteMarkerKind.NONE));

        RouteEdsmWindow.apply(rows, window);

        assertEquals(RouteScanStatus.PENDING, rows.get(1).status);
        assertEquals(RouteScanStatus.PENDING, rows.get(2).status);
        assertTrue(rows.get(1).isSynthetic);
        assertTrue(RouteEdsmWindow.isScanIconRow(rows.get(1)));
    }

    @Test
    void syntheticCurrentStartsTheWindow() {
        List<RouteEntry> rows = routeRows(3);
        rows.add(1, RouteEntry.syntheticSystem("mid", 50L, null, RouteMarkerKind.CURRENT));

        RouteEdsmWindow.apply(rows, 1);

        assertEquals(RouteScanStatus.DEFERRED, rows.get(0).status);
        assertEquals(RouteScanStatus.PENDING, rows.get(1).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(2).status);
    }

    private static List<RouteEntry> routeRows(int count) {
        List<RouteEntry> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new RouteEntry(i, "System " + i, i + 1L, "K", i, RouteScanStatus.PENDING));
        }
        return rows;
    }
}
