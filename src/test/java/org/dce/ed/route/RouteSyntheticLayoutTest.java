package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteSyntheticLayoutTest {

    @Test
    void syntheticCurrentInsertedWithKnownCoordsAlongSegment() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("A", 1L, 0, 0, 0));
        base.add(coordRow("B", 2L, 2, 0, 0));
        RouteCoordsResolver resolver = (name, addr, pref) -> new Double[] { 1.0, 0.0, 0.0 };
        RouteTargetState ts = new RouteTargetState();
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Mid",
                0L,
                null,
                ts,
                null,
                0L,
                resolver,
                false);
        assertEquals(3, out.size());
        assertTrue(out.stream().anyMatch(e -> "Mid".equals(e.systemName) && e.isSynthetic));
    }

    @Test
    void renumberDisplayIndexesSkipsSynthetic() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(coordRow("A", 1L, 0, 0, 0));
        RouteEntry syn = RouteEntry.syntheticSystem("X", 0L, new Double[] { 1.0, 0.0, 0.0 }, RouteMarkerKind.NONE);
        rows.add(syn);
        rows.add(coordRow("B", 2L, 2, 0, 0));
        RouteGeometry.renumberDisplayIndexes(rows);
        assertEquals(Integer.valueOf(1), rows.get(0).displayIndex);
        assertEquals(null, rows.get(1).displayIndex);
        assertEquals(Integer.valueOf(2), rows.get(2).displayIndex);
    }

    private static RouteEntry coordRow(String name, long addr, double x, double y, double z) {
        RouteEntry e = new RouteEntry();
        e.systemName = name;
        e.systemAddress = addr;
        e.x = x;
        e.y = y;
        e.z = z;
        e.isBodyRow = false;
        e.isSynthetic = false;
        return e;
    }
}
