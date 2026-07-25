package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * Goal progress from real EngineerCraft sequences (Journal.2026-07-22).
 */
class EngineeringGoalProgressJournalReplayTest {

    private static final String DIRTY =
            "/engineering/journal/craft-progress-engine-dirty-size5-2026-07-22.json";
    private static final String FSD =
            "/engineering/journal/craft-progress-fsd-longrange-2026-07-22.json";

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void dirtyDriveThrusters_craftsAdvanceToGrade5Complete() {
        JsonObject fixture = JournalFixtureSupport.readObject(DIRTY);
        List<String> craftLines = JournalFixtureSupport.stringList(fixture, "craftLines");
        assertTrue(craftLines.size() >= 20, "expected full G1→G5 Dirty Drive sequence");

        assertTrue(EngineeringJournalBlueprintResolver.resolve(
                "MainEngines", "int_engine_size5_class5", "Engine_Dirty", db).isPresent());

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "thrusters-dirty-drive-tuning-g5",
                "Thrusters",
                "Dirty Drive Tuning",
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                7L,
                "Anaconda · Exception Handler",
                true));

        int applied = 0;
        for (String line : craftLines) {
            EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(line);
            if (EngineeringGoalProgress.applyCraft(goals, craft, db, 7L)) {
                applied++;
            }
        }
        assertTrue(applied >= 15, "most crafts should advance the Dirty Drive goal, applied=" + applied);

        EngineeringGoal done = goals.get(0);
        assertEquals(5, done.getFromGrade(), "final journal crafts reach G5 @ Quality 1.0");
        assertTrue(done.isCurrentUnitComplete() || done.isComplete(),
                "G5 complete unit for Dirty Drive Tuning");
    }

    @Test
    void dirtyDriveThrusters_loadoutSyncMatchesCraftedGrade() {
        JsonObject fixture = JournalFixtureSupport.readObject(DIRTY);
        String loadoutLine = fixture.has("loadoutLine") && !fixture.get("loadoutLine").isJsonNull()
                ? fixture.get("loadoutLine").getAsString()
                : null;
        assertFalse(loadoutLine == null || loadoutLine.isBlank(), "fixture needs Anaconda loadout");

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "thrusters-dirty-drive-tuning-g5",
                "Thrusters",
                "Dirty Drive Tuning",
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                7L,
                "Anaconda · Exception Handler",
                true));

        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(loadoutLine);
        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        assertEquals(5, goals.get(0).getFromGrade(),
                "Anaconda loadout Dirty engines should mark Dirty Drive G5 complete");
    }

    @Test
    void fsdLongRange_craftsAdvanceTowardGrade5() {
        JsonObject fixture = JournalFixtureSupport.readObject(FSD);
        List<String> craftLines = JournalFixtureSupport.stringList(fixture, "craftLines");
        assertTrue(craftLines.size() >= 10);

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "frame-shift-drive-increased-fsd-range-g5",
                "Frame Shift Drive",
                "Increased FSD Range",
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                -1L,
                "",
                true));

        int applied = 0;
        for (String line : craftLines) {
            EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(line);
            if (EngineeringGoalProgress.applyCraft(goals, craft, db)) {
                applied++;
            }
        }
        assertTrue(applied >= 8, "FSD long-range crafts should advance the goal, applied=" + applied);
        assertTrue(goals.get(0).getFromGrade() >= 4,
                "sequence reaches high grade, fromGrade=" + goals.get(0).getFromGrade());
    }
}
