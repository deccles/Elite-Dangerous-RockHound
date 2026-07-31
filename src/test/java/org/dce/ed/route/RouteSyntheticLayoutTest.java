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
    void syntheticCurrentInsertedBeforeLonePastedHop() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Colonia", 2L, 100, 0, 0));
        RouteCoordsResolver resolver = (name, addr, pref) -> new Double[] { 0.0, 0.0, 0.0 };
        RouteTargetState ts = new RouteTargetState();
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Sol",
                1L,
                null,
                ts,
                null,
                0L,
                resolver,
                false);
        assertEquals(2, out.size());
        assertEquals("Sol", out.get(0).systemName);
        assertTrue(out.get(0).isSynthetic);
        assertEquals("Colonia", out.get(1).systemName);
    }

    @Test
    void renumberDisplayIndexesSkipsSynthetic() {
        List<RouteEntry> rows = new ArrayList<>();
        rows.add(coordRow("A", 1L, 0, 0, 0));
        RouteEntry syn = RouteEntry.syntheticSystem("X", 0L, new Double[] { 1.0, 0.0, 0.0 }, RouteMarkerKind.NONE);
        rows.add(syn);
        rows.add(coordRow("B", 2L, 2, 0, 0));
        RouteGeometry.renumberDisplayIndexes(rows);
        assertEquals(Integer.valueOf(0), rows.get(0).displayIndex);
        assertEquals(null, rows.get(1).displayIndex);
        assertEquals(Integer.valueOf(1), rows.get(2).displayIndex);
    }

    @Test
    void destinationStationShownUnderDestSystemWhileEnRoute() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Sol", 1L, 0, 0, 0));
        base.add(coordRow("Diaguandri", 2L, 10, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence(null, null, 2L, 5, "Ray Gateway");
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Sol",
                1L,
                null,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false);
        assertEquals(3, out.size());
        assertEquals("Sol", out.get(0).systemName);
        assertEquals("Diaguandri", out.get(1).systemName);
        assertEquals("Ray Gateway", out.get(2).systemName);
        assertTrue(out.get(2).isBodyRow);
        assertEquals(1, out.get(2).indentLevel);
    }

    @Test
    void destinationStationShownUnderDestSystemWhenInSystem() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Diaguandri", 2L, 10, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence(null, null, 2L, 5, "Ray Gateway");
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Diaguandri",
                2L,
                null,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false);
        assertEquals(2, out.size());
        assertEquals("Diaguandri", out.get(0).systemName);
        assertEquals("Ray Gateway", out.get(1).systemName);
        assertTrue(out.get(1).isBodyRow);
    }

    @Test
    void destinationStationOmittedWhenDestSystemNotOnRoute() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Sol", 1L, 0, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence(null, null, 99L, 5, "Ray Gateway");
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Sol",
                1L,
                null,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false);
        assertEquals(1, out.size());
        assertEquals("Sol", out.get(0).systemName);
        assertTrue(out.stream().noneMatch(e -> e != null && e.isBodyRow));
    }

    @Test
    void destinationBodyOmittedWhenNameMatchesDestinationSystem() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Sol", 1L, 0, 0, 0));
        base.add(coordRow("Diaguandri", 2L, 10, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        // Status often sets Body to the primary star when locking a system jump.
        ts.restoreFromPersistence(null, null, 2L, 1, "Diaguandri");
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Sol",
                1L,
                null,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false);
        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(e -> e != null && e.isBodyRow));
    }

    @Test
    void syntheticTargetGetsFsdStarClassForFuelScoop() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("HIP 12099", 1L, 0, 0, 0));
        base.add(coordRow("Deciat", 5L, 50, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence("Arietis Sector KH-V b2-1", 99L, null, null, null);
        // Simulate FsdTarget star class without constructing a journal event.
        ts.applyFsdTargetEvent(
                new org.dce.ed.logreader.event.FsdTargetEvent(
                        java.time.Instant.EPOCH,
                        new com.google.gson.JsonObject(),
                        "Arietis Sector KH-V b2-1",
                        99L,
                        "M",
                        4),
                false,
                false);
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "HIP 12099",
                1L,
                null,
                ts,
                null,
                0L,
                (name, addr, pref) -> new Double[] { 10.0, 0.0, 0.0 },
                false);
        RouteEntry side = out.stream()
                .filter(e -> e != null && "Arietis Sector KH-V b2-1".equals(e.systemName))
                .findFirst()
                .orElseThrow();
        assertTrue(side.isSynthetic);
        assertEquals("M", side.starClass);
        assertTrue(FuelScoopStarClass.isFuelScoopable(side.starClass));
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
