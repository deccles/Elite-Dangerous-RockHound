package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.testutil.ScanEventFixtures;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class JournalSystemRescanFilterTest {

    @Test
    void filter_keepsMatchingScanAndHonk() {
        ScanEvent scan = ScanEventFixtures.planetScan(
                6, "Eol Prou LW-L c8-75 6", "Eol Prou LW-L c8-75", 424242L, 100.0, "Class I gas giant", List.of());
        FssDiscoveryScanEvent honk = new FssDiscoveryScanEvent(
                Instant.parse("2026-01-01T00:00:01Z"),
                new JsonObject(),
                1.0,
                12,
                2,
                "Eol Prou LW-L c8-75",
                424242L);
        FsdJumpEvent otherJump = new FsdJumpEvent(
                Instant.parse("2026-01-01T00:00:02Z"),
                new JsonObject(),
                "Somewhere Else",
                999L,
                new double[] { 1, 2, 3 },
                "Somewhere Else",
                0,
                "Star",
                10.0,
                1.0,
                50.0,
                null);

        List<EliteLogEvent> filtered = JournalSystemRescanFilter.filterForSystemRescan(
                List.of(scan, honk, otherJump),
                "Eol Prou LW-L c8-75",
                424242L);

        assertEquals(2, filtered.size());
        assertTrue(filtered.contains(scan));
        assertTrue(filtered.contains(honk));
        assertFalse(filtered.contains(otherJump));
    }

    @Test
    void belongsToSystem_matchesByNameWhenAddressMissingOnScan() {
        ScanEvent scan = ScanEventFixtures.starScan(
                0, "Eol Prou LW-L c8-75", "Eol Prou LW-L c8-75", 0L, "M", List.of());
        assertTrue(JournalSystemRescanFilter.belongsToSystem(
                scan, "eol prou lw-l c8-75", 424242L));
    }
}
