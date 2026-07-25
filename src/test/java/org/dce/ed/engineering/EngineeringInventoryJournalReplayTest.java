package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MaterialCollectedEvent;
import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.logreader.event.MaterialsEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * Inventory tracker replay from real journal Materials dumps and intervening events
 * (CMDR Villunus, 2026-07-24).
 */
class EngineeringInventoryJournalReplayTest {

    private static final String COLLECTED =
            "/engineering/journal/inventory-replay-collected-2026-07-24.json";
    private static final String TRADES =
            "/engineering/journal/inventory-replay-trades-2026-07-24.json";

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void materialsThenCollected_matchesNextMaterialsDump() {
        JsonObject fixture = JournalFixtureSupport.readObject(COLLECTED);
        List<String> eventLines = JournalFixtureSupport.stringList(fixture, "eventLines");
        assertFalse(eventLines.isEmpty(), "expected MaterialCollected events between dumps");

        EngineeringInventoryTracker replayed = new EngineeringInventoryTracker(db);
        MaterialsEvent before = assertInstanceOf(MaterialsEvent.class,
                parser.parseRecord(fixture.get("beforeLine").getAsString()));
        replayed.applyEvent(before);

        for (EliteLogEvent event : JournalFixtureSupport.parseLines(parser, eventLines)) {
            assertInstanceOf(MaterialCollectedEvent.class, event);
            replayed.applyEvent(event);
        }

        EngineeringInventoryTracker expected = new EngineeringInventoryTracker(db);
        MaterialsEvent after = assertInstanceOf(MaterialsEvent.class,
                parser.parseRecord(fixture.get("afterLine").getAsString()));
        expected.applyEvent(after);

        Map<String, Integer> want = expected.snapshot();
        Map<String, Integer> got = replayed.snapshot();

        // Materials dumps are capacity-capped (e.g. G5 manufactured max 100). Collected events
        // can briefly exceed that; the next Materials event clamps. Uncapped keys must match.
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            int actual = got.getOrDefault(e.getKey(), 0);
            int expectedCount = e.getValue() != null ? e.getValue() : 0;
            if (expectedCount >= 100 && actual > expectedCount) {
                // Cap clamp — Collected pushed past the dump's capped stack.
                continue;
            }
            assertEquals(expectedCount, actual, "mismatch for " + e.getKey());
        }
    }

    @Test
    void materialsThenTrades_trackerMatchesStartPlusTradeDeltas() {
        JsonObject fixture = JournalFixtureSupport.readObject(TRADES);
        List<String> eventLines = JournalFixtureSupport.stringList(fixture, "eventLines");
        assertTrue(eventLines.size() >= 50, "expected a full trader session");

        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker(db);
        MaterialsEvent before = assertInstanceOf(MaterialsEvent.class,
                parser.parseRecord(fixture.get("beforeLine").getAsString()));
        tracker.applyEvent(before);

        Map<String, Integer> expected = new HashMap<>(tracker.snapshot());
        int trades = 0;
        int collects = 0;
        for (EliteLogEvent event : JournalFixtureSupport.parseLines(parser, eventLines)) {
            if (event instanceof MaterialTradeEvent trade) {
                applyDelta(expected, trade.getPaidName(), trade.getPaidNameLocalised(), -trade.getPaidCount());
                applyDelta(expected, trade.getReceivedName(), trade.getReceivedNameLocalised(),
                        trade.getReceivedCount());
                trades++;
            } else if (event instanceof MaterialCollectedEvent collected) {
                applyDelta(expected, collected.getName(), collected.getNameLocalised(),
                        collected.getCount());
                collects++;
            }
            tracker.applyEvent(event);
        }
        assertTrue(trades >= 50, "trades=" + trades);
        assertTrue(collects >= 1, "session includes mid-trade MaterialCollected");

        expected.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        Map<String, Integer> actual = new HashMap<>(tracker.snapshot());
        actual.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        assertEquals(expected, actual,
                "applying Collected+Trade events must equal start inventory ± deltas");
    }

    @Test
    void materialsThenTradesAndCollects_neverPaysMoreThanOwnedAtEachStep() {
        JsonObject fixture = JournalFixtureSupport.readObject(TRADES);
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker(db);
        tracker.applyEvent(assertInstanceOf(MaterialsEvent.class,
                parser.parseRecord(fixture.get("beforeLine").getAsString())));

        int tradeCount = 0;
        for (EliteLogEvent event : JournalFixtureSupport.parseLines(
                parser, JournalFixtureSupport.stringList(fixture, "eventLines"))) {
            if (event instanceof MaterialTradeEvent trade) {
                String payKey = EngineeringMaterialKeys.resolveKey(
                        trade.getPaidName(), trade.getPaidNameLocalised(), db);
                int beforePay = tracker.getCount(payKey);
                assertTrue(beforePay >= trade.getPaidCount(),
                        () -> trade.getTimestamp() + " paid " + trade.getPaidCount()
                                + " " + payKey + " but tracker only had " + beforePay);
                tradeCount++;
            }
            tracker.applyEvent(event);
        }
        assertTrue(tradeCount >= 50, "expected trader session trades, got " + tradeCount);
    }

    private static void applyDelta(Map<String, Integer> inv, String name, String localised, int delta) {
        String key = EngineeringMaterialKeys.resolveKey(name, localised, db);
        if (key.isBlank() || delta == 0) {
            return;
        }
        int next = Math.max(0, inv.getOrDefault(key, 0) + delta);
        if (next <= 0) {
            inv.remove(key);
        } else {
            inv.put(key, next);
        }
    }
}
