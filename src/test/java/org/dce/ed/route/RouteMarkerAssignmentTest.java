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
                0,
                null, 0L,
                null, null, null,
                null, 0L,
                true);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, rows.get(1).markerKind);
    }

    @Test
    void nextHopPendingWhenNoSideTripAndNotCharging() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("A", 1L, 0, 0, 0));
        rows.add(entry("B", 2L, 1, 0, 0));
        rows.add(entry("C", 3L, 2, 0, 0));
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "B", 2L,
                1,
                null, 0L,
                null, null, null,
                null, 0L,
                false);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(1).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, rows.get(2).markerKind);
    }

    @Test
    void fsdTargetGatingWhenNotChargingUsesTargetMarker() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("Sol", 1L, 0, 0, 0));
        rows.add(entry("Side", 99L, 2, 0, 0));
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "Sol", 1L,
                0,
                "Side", 99L,
                null, null, null,
                null, 0L,
                false);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.TARGET, rows.get(1).markerKind);
    }

    @Test
    void loopedRoute_currentUsesBaseIndexNotFirstNameMatch() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("Gyll", 1L, 0, 0, 0));
        rows.add(entry("Fliese", 2L, 1, 0, 0));
        rows.add(entry("Gyll", 1L, 2, 0, 0));
        rows.add(entry("Fliese", 2L, 3, 0, 0));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).index = i;
        }
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "Gyll", 1L,
                2,
                null, 0L,
                null, null, null,
                null, 0L,
                false);
        assertEquals(RouteMarkerKind.NONE, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(2).markerKind);
        assertEquals(RouteMarkerKind.PENDING_JUMP, rows.get(3).markerKind);
    }

    @Test
    void loopedRoute_fsdTargetUsesForwardOccurrenceNotFirstNameMatch() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(entry("Gliese", 1L, 0, 0, 0));
        rows.add(entry("Core", 2L, 1, 0, 0));
        rows.add(entry("Gliese", 1L, 2, 0, 0));
        rows.add(entry("Core", 2L, 3, 0, 0));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).index = i;
        }
        // Sitting at Core (index 1) with FSD locked on Gliese — mark the upcoming Gliese, not row 0.
        RouteMarkerAssignment.applyMarkerKinds(rows,
                "Core", 2L,
                1,
                "Gliese", 1L,
                null, null, null,
                null, 0L,
                false);
        assertEquals(RouteMarkerKind.NONE, rows.get(0).markerKind);
        assertEquals(RouteMarkerKind.CURRENT, rows.get(1).markerKind);
        assertEquals(RouteMarkerKind.TARGET, rows.get(2).markerKind);
        assertEquals(RouteMarkerKind.NONE, rows.get(3).markerKind);
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
