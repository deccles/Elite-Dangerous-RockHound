package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * Slot binding from a real Anaconda · Exception Handler Loadout + session goals.
 */
class EngineeringGoalSlotMatcherJournalTest {

    private static final String LOADOUT =
            "/engineering/journal/loadout-anaconda-exception-handler.json";
    private static final String GOALS =
            "/engineering/journal/goals-anaconda-exception-handler.json";

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void anacondaLoadout_assignsSessionGoalsToMatchingSlots() {
        JsonObject fixture = JournalFixtureSupport.readObject(LOADOUT);
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(fixture.get("loadoutLine").getAsString());
        assertEquals(7L, loadout.getShipId());

        ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, db);
        List<Row> rows = summary.rows();
        assertFalse(rows.isEmpty());

        List<EngineeringGoal> goals = JournalFixtureSupport.loadGoals(GOALS);
        assertEquals(7, goals.size());

        Map<String, EngineeringGoal> assigned = EngineeringGoalSlotMatcher.assign(rows, goals);
        assertFalse(assigned.isEmpty(), "unscoped session goals should bind to loadout rows");

        EngineeringGoal armour = findGoal(goals, "Armour", "Heavy Duty");
        EngineeringGoal lifeSupport = findGoal(goals, "Life Support", "Lightweight");
        EngineeringGoal sensors = findGoal(goals, "Sensors", "Light Weight Scanner");
        EngineeringGoal hrp = findGoal(goals, "Hull Reinforcement Package", "Heavy Duty Hull Reinforcement");
        EngineeringGoal scb = findGoal(goals, "Shield Cell Bank", "Rapid Charge");
        EngineeringGoal boosterHd = findGoal(goals, "Shield Booster", "Heavy Duty");
        EngineeringGoal boosterRes = findGoal(goals, "Shield Booster", "Resistance Augmented");

        assertRowHasGoal(assigned, rows, armour);
        assertRowHasGoal(assigned, rows, lifeSupport);
        assertRowHasGoal(assigned, rows, sensors);
        assertRowHasGoal(assigned, rows, hrp);
        assertRowHasGoal(assigned, rows, scb);
        assertRowHasGoal(assigned, rows, boosterHd);
        assertRowHasGoal(assigned, rows, boosterRes);

        // Resistance goal must land on a Resistive / Resistance Augmented booster row, not Heavy Duty.
        Row resRow = rowForGoal(assigned, rows, boosterRes);
        assertNotNull(resRow);
        assertTrue(
                EngineeringJournalBlueprintResolver.normalizeToken(resRow.blueprintLabel())
                        .contains("resistance")
                        || resRow.band() == Band.GAP,
                "Resistance Augmented goal bound to " + resRow.slotKey()
                        + " blueprint=" + resRow.blueprintLabel());

        Row hdRow = rowForGoal(assigned, rows, boosterHd);
        assertNotNull(hdRow);
        assertTrue(
                EngineeringJournalBlueprintResolver.normalizeToken(hdRow.blueprintLabel())
                        .contains("heavyduty")
                        || hdRow.band() == Band.GAP,
                "Heavy Duty booster goal bound to " + hdRow.slotKey()
                        + " blueprint=" + hdRow.blueprintLabel());
        assertTrue(!resRow.slotKey().equalsIgnoreCase(hdRow.slotKey()),
                "distinct booster goals must not share a slot");
    }

    @Test
    void anacondaLoadout_gapModulesMatchUnengineeredGoals() {
        JsonObject fixture = JournalFixtureSupport.readObject(LOADOUT);
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(fixture.get("loadoutLine").getAsString());
        List<Row> rows = ShipEngineeringSummary.fromLoadout(loadout, db).rows();
        List<EngineeringGoal> goals = JournalFixtureSupport.loadGoals(GOALS);

        Map<String, EngineeringGoal> assigned = EngineeringGoalSlotMatcher.assign(rows, goals);
        EngineeringGoal lifeSupport = findGoal(goals, "Life Support", "Lightweight");
        Row lsRow = rowForGoal(assigned, rows, lifeSupport);
        assertNotNull(lsRow);
        assertEquals(Band.GAP, lsRow.band(), "Life Support is unengineered on this loadout");
        assertTrue(EngineeringJournalBlueprintResolver.sameModuleType(
                "Life Support", lsRow.moduleType()));
    }

    private static EngineeringGoal findGoal(List<EngineeringGoal> goals, String module, String blueprint) {
        return goals.stream()
                .filter(g -> module.equals(g.getModuleType()) && blueprint.equals(g.getBlueprintName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing goal " + module + " / " + blueprint));
    }

    private static void assertRowHasGoal(Map<String, EngineeringGoal> assigned,
                                         List<Row> rows,
                                         EngineeringGoal goal) {
        assertNotNull(rowForGoal(assigned, rows, goal),
                "no loadout row assigned for " + goal.getModuleType() + " / " + goal.getBlueprintName());
    }

    private static Row rowForGoal(Map<String, EngineeringGoal> assigned,
                                  List<Row> rows,
                                  EngineeringGoal goal) {
        for (Row row : rows) {
            if (assigned.get(EngineeringGoalSlotMatcher.rowKey(row)) == goal) {
                return row;
            }
        }
        return null;
    }
}
