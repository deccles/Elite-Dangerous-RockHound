package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Panther Mk II armour Deep Plating apply (journal uses {@code special_armour_chunky}).
 */
class EngineeringGoalProgressDeepPlatingCraftTest {

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    private static final String DEEP_PLATING_APPLY = """
            {
              "timestamp": "2026-07-27T18:07:03Z",
              "event": "EngineerCraft",
              "Slot": "Armour",
              "Module": "panthermkii_armour_grade3",
              "ApplyExperimentalEffect": "special_armour_chunky",
              "Ingredients": [
                { "Name": "compactcomposites", "Name_Localised": "Compact Composites", "Count": 5 },
                { "Name": "mechanicalequipment", "Name_Localised": "Mechanical Equipment", "Count": 3 },
                { "Name": "molybdenum", "Count": 2 }
              ],
              "Engineer": "Selene Jean",
              "EngineerID": 300210,
              "BlueprintID": 128673644,
              "BlueprintName": "Armour_HeavyDuty",
              "Level": 5,
              "Quality": 1.000000,
              "ExperimentalEffect": "special_armour_chunky",
              "ExperimentalEffect_Localised": "Deep Plating"
            }
            """;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void applyCraft_marksArmourHeavyDutyDeepPlatingComplete() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(DEEP_PLATING_APPLY);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "armour-heavy-duty-g5",
                "Armour",
                "Heavy Duty",
                5,
                0,
                5,
                "armour-deep-plating-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_marksDeepPlatingWithoutLocalisedViaIngredientsWhenApplySet() {
        String json = DEEP_PLATING_APPLY.replace(
                "\"ExperimentalEffect_Localised\": \"Deep Plating\"",
                "\"ExperimentalEffect_Localised\": \"\"");
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        assertTrue(craft.getExperimentalEffectLocalised() == null
                || craft.getExperimentalEffectLocalised().isBlank());
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "armour-heavy-duty-g5",
                "Armour",
                "Heavy Duty",
                5,
                0,
                5,
                "armour-deep-plating-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied(),
                "ApplyExperimentalEffect + Deep Plating ingredients should mark experimental");
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_doesNotMarkCompleteForWrongExperimentalGoal() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(DEEP_PLATING_APPLY);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "armour-heavy-duty-g5",
                "Armour",
                "Heavy Duty",
                5,
                0,
                5,
                "armour-layered-plating-experimental"));

        EngineeringGoalProgress.applyCraft(goals, craft, db);
        assertFalse(goals.get(0).isExperimentalApplied());
        assertFalse(goals.get(0).isComplete());
    }
}
