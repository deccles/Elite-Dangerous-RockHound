package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteEdsmWindowTest {

    @Test
    void onlyCurrentAndNextTwentyFourSystemRowsAreQueryable() {
        List<RouteEntry> rows = routeRows(30);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;

        RouteEdsmWindow.apply(rows, 25);

        assertEquals(RouteScanStatus.PENDING, rows.get(24).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(25).status);
        assertEquals(25, rows.stream().filter(row -> row.status.needsEdsmQuery()).count());
    }

    @Test
    void advancingCurrentPromotesOneNewRowWithoutChangingResolvedRowsBehind() {
        List<RouteEntry> rows = routeRows(30);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        RouteEdsmWindow.apply(rows, 25);
        rows.get(0).status = RouteScanStatus.UNKNOWN;
        rows.get(0).markerKind = RouteMarkerKind.NONE;
        rows.get(1).markerKind = RouteMarkerKind.CURRENT;

        RouteEdsmWindow.apply(rows, 25);

        assertEquals(RouteScanStatus.UNKNOWN, rows.get(0).status);
        assertEquals(RouteScanStatus.PENDING, rows.get(25).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(26).status);
    }

    @Test
    void syntheticAndBodyRowsDoNotConsumeWindowSlots() {
        List<RouteEntry> rows = routeRows(26);
        rows.get(0).markerKind = RouteMarkerKind.CURRENT;
        rows.add(1, RouteEntry.syntheticBody("body"));
        rows.add(2, RouteEntry.syntheticSystem("detour", 999L, null, RouteMarkerKind.NONE));

        RouteEdsmWindow.apply(rows, 25);

        assertEquals(RouteScanStatus.PENDING, rows.get(26).status);
        assertEquals(RouteScanStatus.DEFERRED, rows.get(27).status);
    }

    private static List<RouteEntry> routeRows(int count) {
        List<RouteEntry> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new RouteEntry(i, "System " + i, i + 1L, "K", i, RouteScanStatus.PENDING));
        }
        return rows;
    }
}
