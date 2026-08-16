package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteSyntheticLayoutTest {

    @Test
    void loopTargetAtFirstRow_reusesOriginalRowAndKeepsCurrentAtEnd() {
        RouteSession session = new RouteSession(null, j -> false);
        session.replaceBaseRouteEntries(List.of(
                coordRow("Alpha", 1L, 0, 0, 0),
                coordRow("Beta", 2L, 10, 0, 0),
                coordRow("Gamma", 3L, 20, 0, 0)));
        session.setCustomRouteLoopEnabledForArrivals(true);
        session.applyKnownCurrentSystem("Alpha", 1L, null);
        session.applyKnownCurrentSystem("Beta", 2L, null);
        session.applyKnownCurrentSystem("Gamma", 3L, null);
        session.getTargetState().restoreFromPersistence("Alpha", 1L, null, null, null);

        List<RouteEntry> out = session.buildDisplaySnapshot(null, (n, a, p) -> null, true)
                .displayedEntries();

        assertEquals(3, out.size());
        assertFalse(out.stream().anyMatch(e -> e != null && e.isSynthetic));
        assertEquals(RouteMarkerKind.TARGET, out.get(0).markerKind);
        assertEquals(RouteMarkerKind.CURRENT, out.get(2).markerKind);
        assertEquals(20.0, RouteGeometry.cumulativeDistanceLy(out, 0, 2), 0.0001);
    }

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
                0,
                ts,
                null,
                0L,
                resolver,
                false,
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
                0,
                ts,
                null,
                0L,
                resolver,
                false,
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
                0,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false,
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
                0,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false,
                false);
        assertEquals(2, out.size());
        assertEquals("Diaguandri", out.get(0).systemName);
        assertEquals("Ray Gateway", out.get(1).systemName);
        assertTrue(out.get(1).isBodyRow);
    }

    @Test
    void destinationStationUnderCurrentVisit_notPastLoopHop() {
        // Loop: Core → Gliese → Core → Gliese. At second Core, Hyperion must sit under hop 2.
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Core Sys Sector CB-O a6-1", 1L, 0, 0, 0));
        base.add(coordRow("Gliese 868", 2L, 10, 0, 0));
        base.add(coordRow("Core Sys Sector CB-O a6-1", 1L, 20, 0, 0));
        base.add(coordRow("Gliese 868", 2L, 30, 0, 0));
        for (int i = 0; i < base.size(); i++) {
            base.get(i).index = i;
        }
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence(null, null, 1L, 7, "Hyperion");
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Core Sys Sector CB-O a6-1",
                1L,
                null,
                2,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                true,
                false);
        assertEquals(5, out.size());
        assertEquals("Core Sys Sector CB-O a6-1", out.get(0).systemName);
        assertEquals("Gliese 868", out.get(1).systemName);
        assertEquals("Core Sys Sector CB-O a6-1", out.get(2).systemName);
        assertEquals("Hyperion", out.get(3).systemName);
        assertTrue(out.get(3).isBodyRow);
        assertEquals(1, out.get(3).indentLevel);
        assertEquals("Gliese 868", out.get(4).systemName);
        assertTrue(out.stream().noneMatch(e -> e != null && e.isBodyRow && out.indexOf(e) < 2));
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
                0,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false,
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
                0,
                ts,
                null,
                0L,
                (name, addr, pref) -> null,
                false,
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
                0,
                ts,
                null,
                0L,
                (name, addr, pref) -> new Double[] { 10.0, 0.0, 0.0 },
                false,
                false);
        RouteEntry side = out.stream()
                .filter(e -> e != null && "Arietis Sector KH-V b2-1".equals(e.systemName))
                .findFirst()
                .orElseThrow();
        assertTrue(side.isSynthetic);
        assertEquals("M", side.starClass);
        assertTrue(FuelScoopStarClass.isFuelScoopable(side.starClass));
    }

    /**
     * Normal NavRoute side trip: place by 3D polyline distance even when that is after a later hop.
     */
    @Test
    void nonCustom_syntheticTargetUsesGeometricInsertion() {
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("A", 1L, 0, 0, 0));
        base.add(coordRow("B", 2L, 100, 0, 0));
        base.add(coordRow("C", 3L, 200, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence("Side", 99L, null, null, null);
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "A",
                1L,
                null,
                0,
                ts,
                null,
                0L,
                // Closer to B→C than A→B.
                (name, addr, pref) -> new Double[] { 140.0, 0.0, 0.0 },
                false,
                false);
        assertEquals(4, out.size());
        assertEquals("A", out.get(0).systemName);
        assertEquals("B", out.get(1).systemName);
        assertEquals("Side", out.get(2).systemName);
        assertTrue(out.get(2).isSynthetic);
        assertEquals("C", out.get(3).systemName);
    }

    /**
     * Multi-jump NavRoute to the next custom destination: the FSD intermediate must appear
     * after current and before that destination — even when coords are closer to a later segment.
     */
    @Test
    void customRoute_syntheticTargetInsertedAfterCurrent_notAfterNextCustomDestination() {
        // Custom: Core → JD-I → Gyllembo. Intermediate LY-H is nearer JD-I→Gyllembo than Core→JD-I,
        // so geometric insertion would wrongly place it after JD-I.
        List<RouteEntry> base = new ArrayList<>();
        base.add(coordRow("Core Sys Sector KC-M A7-4", 1L, 0, 0, 0));
        base.add(coordRow("Piscium Sector JD-I a10-1", 2L, 100, 0, 0));
        base.add(coordRow("Gyllembo", 3L, 200, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        ts.restoreFromPersistence("Piscium Sector LY-H A10-4", 99L, null, null, null);
        List<RouteEntry> out = RouteLayoutEngine.buildDisplayedEntries(
                base,
                null,
                "Core Sys Sector KC-M A7-4",
                1L,
                null,
                0,
                ts,
                null,
                0L,
                // Closer to JD-I→Gyllembo (midpoint ~150) than Core→JD-I (midpoint ~50).
                (name, addr, pref) -> new Double[] { 140.0, 0.0, 0.0 },
                true,
                false);
        assertEquals(4, out.size());
        assertEquals("Core Sys Sector KC-M A7-4", out.get(0).systemName);
        assertEquals(Integer.valueOf(0), out.get(0).displayIndex);
        assertEquals("Piscium Sector LY-H A10-4", out.get(1).systemName);
        assertTrue(out.get(1).isSynthetic);
        assertEquals(null, out.get(1).displayIndex);
        assertEquals("Piscium Sector JD-I a10-1", out.get(2).systemName);
        assertEquals(Integer.valueOf(1), out.get(2).displayIndex);
        assertEquals("Gyllembo", out.get(3).systemName);
        assertEquals(Integer.valueOf(2), out.get(3).displayIndex);
    }

    @Test
    void customRoute_arrivalAtGeneratedIntermediateMarksThatRowCurrent() {
        RouteSession session = new RouteSession(null, j -> false);
        session.replaceBaseRouteEntries(List.of(
                coordRow("Gliese 868", 1L, 0, 0, 0),
                coordRow("Arietis Sector CO-P b5-1", 4L, 30, 0, 0),
                coordRow("Col 285 Sector CC-J b23-3", 5L, 60, 0, 0)));
        session.replaceCustomNavRouteEntries(List.of(
                coordRow("Gliese 868", 1L, 0, 0, 0),
                coordRow("LTT 569", 2L, 10, 0, 0),
                coordRow("Arietis Sector ZE-A d70", 3L, 20, 0, 0),
                coordRow("Arietis Sector CO-P b5-1", 4L, 30, 0, 0)));
        session.applyKnownCurrentSystem("Gliese 868", 1L, null);
        session.applyKnownCurrentSystem("LTT 569", 2L, new double[] { 10, 0, 0 });

        RouteCoordsResolver resolver = (n, a, p) -> p == null
                ? null
                : new Double[] { p[0], p[1], p[2] };
        List<RouteEntry> out = session.buildDisplaySnapshot(null, resolver, true)
                .displayedEntries();

        int glieseRow = RouteGeometry.findSystemRow(out, "Gliese 868", 1L);
        int lttRow = RouteGeometry.findSystemRow(out, "LTT 569", 2L);
        int destinationRow = RouteGeometry.findSystemRow(out, "Arietis Sector CO-P b5-1", 4L);
        assertEquals(RouteMarkerKind.NONE, out.get(glieseRow).markerKind);
        assertEquals(RouteMarkerKind.CURRENT, out.get(lttRow).markerKind);
        assertTrue(out.get(lttRow).isSynthetic);
        assertEquals(20.0, RouteGeometry.cumulativeDistanceLy(out, lttRow, destinationRow), 0.0001);
    }

    @Test
    void customRoute_unknownSessionCurrentDoesNotDropFirstGameRouteRow() {
        List<RouteEntry> displayed = new ArrayList<>(List.of(
                coordRow("Stale Current", 99L, -10, 0, 0),
                coordRow("Destination", 3L, 20, 0, 0)));
        List<RouteEntry> gameRoute = List.of(
                coordRow("Actual Current", 1L, 0, 0, 0),
                coordRow("Intermediate", 2L, 10, 0, 0),
                coordRow("Destination", 3L, 20, 0, 0));

        RouteLayoutEngine.applyCustomNavRouteRows(
                displayed, gameRoute, "Stale Current", 99L, 0, (n, a, p) -> null);

        RouteEntry actual = displayed.stream()
                .filter(e -> e != null && "Actual Current".equals(e.systemName))
                .findFirst()
                .orElseThrow();
        assertTrue(actual.isSynthetic);
        assertTrue(displayed.stream().anyMatch(
                e -> e != null && e.isSynthetic && "Intermediate".equals(e.systemName)));
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
