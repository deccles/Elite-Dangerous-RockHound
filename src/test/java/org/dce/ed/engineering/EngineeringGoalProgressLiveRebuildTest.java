package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dce.ed.cache.SystemCache;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

/**
 * Live crafts advance one shared progress count on a quantity-2 goal. A roll on the second rack
 * used to stack on the first rack's rolls and mark both racks finished mid-session.
 */
class EngineeringGoalProgressLiveRebuildTest {

    private static final String CLIENT = "live-rebuild-test";
    private static final long SHIP = 19L;

    /** SQLite on Windows can hold the -wal/-shm files briefly after close, so do not fail cleanup. */
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private final EliteLogParser parser = new EliteLogParser();
    private final EngineeringDatabase db = EngineeringDatabase.getInstance();
    private String previousDbProperty;

    @BeforeEach
    void useTempStore() {
        previousDbProperty = System.getProperty(SystemCache.CACHE_DB_PATH_PROPERTY);
        System.setProperty(SystemCache.CACHE_DB_PATH_PROPERTY,
                tempDir.resolve("store.db").toString());
    }

    @AfterEach
    void restoreStore() {
        if (previousDbProperty == null) {
            System.clearProperty(SystemCache.CACHE_DB_PATH_PROPERTY);
        } else {
            System.setProperty(SystemCache.CACHE_DB_PATH_PROPERTY, previousDbProperty);
        }
    }

    @Test
    void secondRackRoll_doesNotFinishTheFirstRack() {
        EngineeringCraftStore.rememberLoadout(CLIENT, (LoadoutEvent) parser.parseRecord("""
                {
                  "timestamp": "2026-09-28T15:00:00Z",
                  "event": "Loadout",
                  "Ship": "panthermkii",
                  "ShipID": 19,
                  "Modules": [
                    {"Slot": "Slot03_Size6", "Item": "int_cargorack_size6_class1",
                     "Engineering": {"Engineer": "Bill Turner", "EngineerID": 399998,
                       "BlueprintName": "CargoRackS6C1_Extended", "Level": 1, "Quality": 1.0}},
                    {"Slot": "Slot04_Size6", "Item": "int_cargorack_size6_class1",
                     "Engineering": {"Engineer": "Bill Turner", "EngineerID": 399998,
                       "BlueprintName": "CargoRackS6C1_Extended", "Level": 1, "Quality": 1.0}}
                  ]
                }
                """));
        // Slot03 part-way through G4; Slot04 finished G3.
        remember("Slot03_Size6", 2, 1.0, "2026-09-28T15:10:00Z");
        remember("Slot03_Size6", 3, 1.0, "2026-09-28T15:11:00Z");
        remember("Slot03_Size6", 4, 0.6, "2026-09-28T15:12:00Z");
        remember("Slot04_Size6", 2, 1.0, "2026-09-28T19:58:44Z");
        remember("Slot04_Size6", 3, 1.0, "2026-09-28T19:59:02Z");

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal("cargo-rack-extended-g5", "Cargo Rack", "Extended",
                0, 0, 4, "", true, false, 2, 0, SHIP, "Panther Clipper Mk II"));
        EngineeringGoalProgress.rebuildMultiUnitGoalsFromStore(goals, CLIENT, db);
        assertEquals(0, goals.get(0).getCompletedUnits(), "neither rack has finished G4");

        liveCraft(goals, "Slot04_Size6", 4, 0.25, "2026-09-28T21:00:00Z");
        assertEquals(0, goals.get(0).getCompletedUnits(),
                "Slot04's first G4 roll must not finish Slot03");

        liveCraft(goals, "Slot03_Size6", 4, 1.0, "2026-09-28T21:01:00Z");
        EngineeringGoal after = goals.get(0);
        assertEquals(1, after.getCompletedUnits(), "only Slot03 has finished G4");
        assertFalse(after.isComplete(), "Slot04 is still mid-G4");
        assertTrue(after.getFromGrade() < after.getTargetGrade());
    }

    /**
     * Every cargo roll from Journal.2026-09-27T212436.01.log with Bill Turner's rank changes. Both
     * racks show all four rows checked in game; Slot03 stopped at Quality 0.901.
     */
    @Test
    void todaysCargoRolls_bothRacksFinishG4() {
        EngineerRankHistory previous = EngineerRankHistory.shared();
        EngineerRankHistory ranks = new EngineerRankHistory();
        ranks.record("Bill Turner", java.time.Instant.parse("2026-09-28T14:57:02Z"), 1);
        ranks.record("Bill Turner", java.time.Instant.parse("2026-09-28T15:14:16Z"), 2);
        ranks.record("Bill Turner", java.time.Instant.parse("2026-09-28T15:56:46Z"), 3);
        ranks.record("Bill Turner", java.time.Instant.parse("2026-09-28T15:57:08Z"), 4);
        ranks.record("Bill Turner", java.time.Instant.parse("2026-09-28T16:03:18Z"), 5);
        EngineerRankHistory.useShared(ranks);
        try {
            String[][] rolls = {
                    {"Slot03_Size6", "2", "0.2006", "2026-09-28T15:17:49Z"},
                    {"Slot03_Size6", "2", "0.4017", "2026-09-28T15:17:53Z"},
                    {"Slot03_Size6", "2", "0.6023", "2026-09-28T15:56:46Z"},
                    {"Slot03_Size6", "2", "0.8526", "2026-09-28T15:56:51Z"},
                    {"Slot03_Size6", "3", "0.1996", "2026-09-28T15:57:00Z"},
                    {"Slot03_Size6", "3", "0.3993", "2026-09-28T15:57:04Z"},
                    {"Slot03_Size6", "3", "0.5989", "2026-09-28T15:57:08Z"},
                    {"Slot03_Size6", "3", "0.8485", "2026-09-28T15:57:11Z"},
                    {"Slot03_Size6", "4", "0.2004", "2026-09-28T15:57:18Z"},
                    {"Slot03_Size6", "4", "0.4012", "2026-09-28T15:57:21Z"},
                    {"Slot03_Size6", "4", "0.6511", "2026-09-28T17:56:02Z"},
                    {"Slot03_Size6", "4", "0.901", "2026-09-28T17:56:07Z"},
                    {"Slot04_Size6", "2", "0.5006", "2026-09-28T19:58:40Z"},
                    {"Slot04_Size6", "2", "1.0", "2026-09-28T19:58:44Z"},
                    {"Slot04_Size6", "3", "0.3335", "2026-09-28T19:58:55Z"},
                    {"Slot04_Size6", "3", "0.667", "2026-09-28T19:58:58Z"},
                    {"Slot04_Size6", "3", "1.0", "2026-09-28T19:59:02Z"},
                    {"Slot04_Size6", "4", "0.2499", "2026-09-28T21:45:04Z"},
                    {"Slot04_Size6", "4", "0.5002", "2026-09-28T21:45:07Z"},
                    {"Slot04_Size6", "4", "0.7501", "2026-09-28T21:45:11Z"},
                    {"Slot04_Size6", "4", "1.0", "2026-09-28T21:45:20Z"},
            };
            for (String[] r : rolls) {
                remember(r[0], Integer.parseInt(r[1]), Double.parseDouble(r[2]), r[3]);
            }
            List<EngineeringGoal> goals = new ArrayList<>();
            goals.add(new EngineeringGoal("cargo-rack-extended-g5", "Cargo Rack", "Extended",
                    0, 0, 4, "", true, false, 2, 0, SHIP, "Panther Clipper Mk II"));
            EngineeringGoalProgress.rebuildMultiUnitGoalsFromStore(goals, CLIENT, db);
            assertEquals(2, goals.get(0).getCompletedUnits());
            assertTrue(goals.get(0).isComplete());
        } finally {
            EngineerRankHistory.useShared(previous);
        }
    }

    /** Same order as the Engineering tab: store the craft, apply it, then rebuild. */
    private void liveCraft(List<EngineeringGoal> goals, String slot, int level, double quality,
                           String timestamp) {
        EngineerCraftEvent craft = remember(slot, level, quality, timestamp);
        EngineeringGoalProgress.applyCraft(goals, craft, db, SHIP);
        EngineeringGoalProgress.rebuildMultiUnitGoalsFromStore(goals, CLIENT, db);
    }

    private EngineerCraftEvent remember(String slot, int level, double quality, String timestamp) {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord("""
                {"timestamp": "%s", "event": "EngineerCraft", "Slot": "%s",
                 "Module": "int_cargorack_size6_class1",
                 "Ingredients": [{"Name": "MechanicalScrap", "Count": 1}],
                 "Engineer": "Bill Turner", "EngineerID": 300010,
                 "BlueprintName": "CargoRackS6C1_Extended", "Level": %d, "Quality": %s}
                """.formatted(timestamp, slot, level, Double.toString(quality)));
        EngineeringCraftStore.rememberCraft(CLIENT, craft, SHIP);
        return craft;
    }
}
