package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteMarkerAssignmentTest {

    @Test
    void pendingJumpNextHopWhenChargingAndNoSideTrip() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("Sol", 1L, 0, 0, 0));
        rows.add(entry("Alpha", 2L, 1, 0, 0));
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "Sol", 1L,
                null, 0L,
                null, null, null,
                null, 0L,
                true);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, rows.get(1).markerKind);
    }

    @Test
    void fsdTargetGatingWhenNotChargingUsesTargetMarker() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("Sol", 1L, 0, 0, 0));
        rows.add(entry("Side", 99L, 2, 0, 0));
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "Sol", 1L,
                "Side", 99L,
                null, null, null,
                null, 0L,
                false);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.TARGET, rows.get(1).markerKind);
    }

    private static RouteEntry entry(String name, long addr, double x, double y, double z) {
        RouteEntry e = new RouteEntry();
        e.systemName = name;
        e.systemAddress = addr;
        e.x = x;
        e.y = y;
        e.z = z;
        e.isBodyRow = false;
        e.isSynthetic = false;
        e.markerKind = RouteMarkerKind.NONE;
        return e;
    }
}
