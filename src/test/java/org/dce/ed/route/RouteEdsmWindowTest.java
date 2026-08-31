package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void syntheticAndBodyRowsDoNotConsumeWindowSlots() {
        int window = RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE;
        List<RouteEntry> rows = routeRows(window + 1);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        rows.add(1, RouteEntry.syntheticBody("body"));
        rows.add(2, RouteEntry.syntheticSystem("detour", 999L, null, RouteMarkerKind.NONE));

        RouteEdsmWindow.apply(rows, window);

        assertEquals(RouteScanStatus.PENDING, rows.get(window + 1).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(window + 2).status);
    }

    private static List<RouteEntry> routeRows(int count) {
        List<RouteEntry> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new RouteEntry(i, "System " + i, i + 1L, "K", i, RouteScanStatus.PENDING));
        }
        return rows;
    }
}
