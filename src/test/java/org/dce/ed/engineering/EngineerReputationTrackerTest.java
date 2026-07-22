package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerProgressEvent;
import org.junit.jupiter.api.Test;

class EngineerReputationTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void appliesSingleRankUpdatesAndFullSnapshot() {
        EngineerReputationTracker tracker = new EngineerReputationTracker();
        EngineerProgressEvent unlocked = (EngineerProgressEvent) parser.parseRecord("""
                {"timestamp":"2026-07-21T19:14:55Z","event":"EngineerProgress",
                 "Engineer":"Marco Qwent","EngineerID":300200,"Progress":"Unlocked","Rank":1}
                """);
        assertTrue(tracker.applyEvent(unlocked));
        assertEquals(1, tracker.rank("Marco Qwent"));

        EngineerProgressEvent rank5 = (EngineerProgressEvent) parser.parseRecord("""
                {"timestamp":"2026-07-21T21:08:15Z","event":"EngineerProgress",
                 "Engineer":"Marco Qwent","EngineerID":300200,"Rank":5}
                """);
        assertTrue(tracker.applyEvent(rank5));
        assertEquals(5, tracker.rank("marco qwent"));

        EngineerProgressEvent snapshot = (EngineerProgressEvent) parser.parseRecord("""
                {"timestamp":"2026-07-22T00:00:00Z","event":"EngineerProgress","Engineers":[
                  {"Engineer":"The Dweller","EngineerID":1,"Progress":"Unlocked","Rank":5,"RankProgress":0},
                  {"Engineer":"Liz Ryder","EngineerID":2,"Progress":"Unlocked","Rank":3,"RankProgress":10}
                ]}
                """);
        assertTrue(tracker.applyEvent(snapshot));
        assertEquals(5, tracker.rank("The Dweller"));
        assertEquals(3, tracker.rank("Liz Ryder"));
        assertEquals(0, tracker.rank("Marco Qwent"), "full snapshot replaces prior ranks");
    }

    @Test
    void bestRankPicksHighestAmongNames() {
        EngineerReputationTracker tracker = new EngineerReputationTracker();
        tracker.applyEvent(new EngineerProgressEvent(
                Instant.parse("2026-07-22T00:00:00Z"),
                new com.google.gson.JsonObject(),
                List.of(
                        new EngineerProgressEvent.EngineerRank("The Dweller", 1, "Unlocked", 5, 0),
                        new EngineerProgressEvent.EngineerRank("Marco Qwent", 2, "Unlocked", 3, 0)),
                false));
        assertEquals(5, tracker.bestRank(List.of("Marco Qwent", "The Dweller", "Unknown")));
    }
}
