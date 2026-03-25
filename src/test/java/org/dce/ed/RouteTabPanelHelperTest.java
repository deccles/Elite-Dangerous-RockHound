package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.route.RouteEntry;
import org.junit.jupiter.api.Test;

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
    void renumberDisplayIndexes_setsDisplayIndexSequentially() {
        List<RouteEntry> entries = new ArrayList<>();
        entries.add(entry("A", 1L));
        entries.add(entry("B", 2L));
        RouteTabPanel.renumberDisplayIndexes(entries);
        assertEquals(Integer.valueOf(1), entries.get(0).displayIndex);
        assertEquals(Integer.valueOf(2), entries.get(1).displayIndex);
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
