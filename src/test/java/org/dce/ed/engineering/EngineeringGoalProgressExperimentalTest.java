package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGoalProgressExperimentalTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;
    private final EliteLogParser parser = new EliteLogParser();

    private static final String FAST_CHARGE_CRAFT = """
            {
              "timestamp": "2026-07-13T15:48:23Z",
              "event": "EngineerCraft",
              "Slot": "Slot01_Size7",
              "Module": "int_shieldgenerator_size7_class3_fast",
              "BlueprintName": "ShieldGenerator_Thermic",
              "ApplyExperimentalEffect": "special_shield_regenerative",
              "ExperimentalEffect": "special_shield_regenerative",
              "ExperimentalEffect_Localised": "Fast Charge",
              "Ingredients": [
                { "Name": "wornshieldemitters", "Count": 5 },
                { "Name": "uncutfocuscrystals", "Count": 3 },
                { "Name": "compoundshielding", "Count": 1 }
              ],
              "Level": 5
            }
            """;

    private static final String THERMO_BLOCK_CRAFT = """
            {
              "timestamp": "2026-07-12T18:37:59Z",
              "event": "EngineerCraft",
              "Slot": "ShieldGenerator",
              "Module": "int_shieldgenerator_size5_class5",
              "BlueprintName": "ShieldGenerator_Thermal",
              "ExperimentalEffect": "special_shieldgenerator_thermoblock",
              "ExperimentalEffect_Localised": "Thermo Block",
              "Ingredients": [
                { "Name": "wornshieldemitters", "Count": 5 },
                { "Name": "uncutfocuscrystals", "Count": 3 },
                { "Name": "heatvanes", "Count": 1 }
              ],
              "Level": 5
            }
            """;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
    }

    @Test
    void parse_engineerCraft_readsExperimentalEffectFields() {
        var event = parser.parseRecord(THERMO_BLOCK_CRAFT);
        assertInstanceOf(EngineerCraftEvent.class, event);
        EngineerCraftEvent craft = (EngineerCraftEvent) event;
        assertEquals("special_shieldgenerator_thermoblock", craft.getExperimentalEffect());
        assertEquals("Thermo Block", craft.getExperimentalEffectLocalised());
    }

    @Test
    void applyCraft_marksFastChargeCompleteFromThermicJournalName() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(FAST_CHARGE_CRAFT);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-fast-charge-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_marksExperimentalAppliedForMatchingGoal() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(THERMO_BLOCK_CRAFT);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_gradeRollWhoseIngredientsCoverExperimentalRecipeDoesNotApplyIt() {
        // A plain grade roll (has a blueprint Level, no experimental-effect fields) must never be
        // inferred as an experimental application just because its ingredients happen to cover the
        // experimental's recipe — that falsely completed multi-unit goals.
        String json = """
                {
                  "timestamp": "2026-07-20T10:00:00Z",
                  "event": "EngineerCraft",
                  "Slot": "SmallHardpoint1",
                  "Module": "hpt_pulselaser_gimbal_small",
                  "Ingredients": [
                    { "Name": "mechanicalscrap", "Count": 5 },
                    { "Name": "mechanicalcomponents", "Count": 3 },
                    { "Name": "ruthenium", "Count": 1 }
                  ],
                  "Engineer": "Tod 'The Blaster' McQuinn",
                  "BlueprintName": "Weapon_RapidFire",
                  "Level": 3,
                  "Quality": 0.4
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "pulse-laser-rapid-fire-modification-g5",
                "Pulse Laser",
                "Rapid Fire Modification",
                2,
                0,
                5,
                "pulse-laser-oversized-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        // Quality 0.4 ⇒ 2/5 rolls into G3 (same mapping as loadout progress).
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());
        assertFalse(goals.get(0).isExperimentalApplied());
        assertFalse(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_legacyEffectlessCraftStillInfersExperimentalFromIngredients() {
        // Old journals without ApplyExperimentalEffect/Level still rely on the ingredient fallback.
        String json = """
                {
                  "timestamp": "2026-07-20T10:05:00Z",
                  "event": "EngineerCraft",
                  "Slot": "SmallHardpoint1",
                  "Module": "hpt_pulselaser_gimbal_small",
                  "Ingredients": [
                    { "Name": "mechanicalscrap", "Count": 5 },
                    { "Name": "mechanicalcomponents", "Count": 3 },
                    { "Name": "ruthenium", "Count": 1 }
                  ],
                  "Engineer": "Tod 'The Blaster' McQuinn",
                  "BlueprintName": "Weapon_RapidFire"
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "pulse-laser-rapid-fire-modification-g5",
                "Pulse Laser",
                "Rapid Fire Modification",
                5,
                0,
                5,
                "pulse-laser-oversized-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void replay_blueprintSwitchWipesInstanceProgressAndExperimental() {
        // Real-world failure: LargeHardpoint1 had Efficient G5 + Oversized, was re-rolled to
        // Focused (destroying the Efficient blueprint and its experimental in game), then rolled
        // back to Efficient G5 without re-applying Oversized. Replay kept the stale "experimental
        // applied" flag and counted the goal 3/3 complete.
        List<org.dce.ed.logreader.EliteLogEvent> events = new ArrayList<>();
        for (String slot : new String[] {"LargeHardpoint1", "LargeHardpoint2", "LargeHardpoint3"}) {
            for (int i = 0; i < 5; i++) {
                events.add(parser.parseRecord(pulseCraft(slot, "Weapon_Efficient", 5, null, false)));
            }
            events.add(parser.parseRecord(pulseCraft(slot, "Weapon_Efficient", 5, "special_weapon_damage", true)));
        }
        // LargeHardpoint1 re-engineered with a different blueprint, then back to Efficient, no experimental.
        events.add(parser.parseRecord(pulseCraft("LargeHardpoint1", "Weapon_Focused", 1, null, false)));
        for (int i = 0; i < 5; i++) {
            events.add(parser.parseRecord(pulseCraft("LargeHardpoint1", "Weapon_Efficient", 5, null, false)));
        }

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "pulse-laser-efficient-weapon-g5",
                "Pulse Laser",
                "Efficient Weapon",
                0,
                0,
                5,
                "pulse-laser-oversized-experimental",
                true,
                false,
                3,
                0));

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, events, db));
        EngineeringGoal goal = goals.get(0);
        assertEquals(2, goal.getCompletedUnits());
        assertFalse(goal.isExperimentalApplied());
        assertFalse(goal.isComplete());
    }

    @Test
    void replay_gradeRollWithoutExperimentalFieldClearsStaleAppliedFlag() {
        // A modern grade roll reports the module's current experimental in ExperimentalEffect.
        // Its absence means the module has none, so an earlier "applied" flag must be dropped
        // even if the blueprint-switch crafts were never recorded.
        List<org.dce.ed.logreader.EliteLogEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            events.add(parser.parseRecord(pulseCraft("LargeHardpoint1", "Weapon_Efficient", 4, null, false)));
        }
        events.add(parser.parseRecord(pulseCraft("LargeHardpoint1", "Weapon_Efficient", 4, "special_weapon_damage", true)));
        for (int i = 0; i < 5; i++) {
            events.add(parser.parseRecord(pulseCraft("LargeHardpoint1", "Weapon_Efficient", 5, null, false)));
        }

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "pulse-laser-efficient-weapon-g5",
                "Pulse Laser",
                "Efficient Weapon",
                0,
                0,
                5,
                "pulse-laser-oversized-experimental",
                true,
                false,
                1,
                0));

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, events, db));
        EngineeringGoal goal = goals.get(0);
        assertFalse(goal.isExperimentalApplied());
        assertFalse(goal.isComplete());
    }

    @Test
    void replay_gradeRollCarryingCurrentExperimentalKeepsAppliedFlag() {
        // LargeHardpoint2/3 case: rolls after an apply carry ExperimentalEffect and must not
        // clear the applied flag.
        List<org.dce.ed.logreader.EliteLogEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            events.add(parser.parseRecord(pulseCraft("LargeHardpoint2", "Weapon_Efficient", 4, null, false)));
        }
        events.add(parser.parseRecord(pulseCraft("LargeHardpoint2", "Weapon_Efficient", 4, "special_weapon_damage", true)));
        for (int i = 0; i < 5; i++) {
            events.add(parser.parseRecord(pulseCraft("LargeHardpoint2", "Weapon_Efficient", 5, "special_weapon_damage", false)));
        }

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "pulse-laser-efficient-weapon-g5",
                "Pulse Laser",
                "Efficient Weapon",
                0,
                0,
                5,
                "pulse-laser-oversized-experimental",
                true,
                false,
                1,
                0));

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, events, db));
        assertTrue(goals.get(0).isComplete());
    }

    /**
     * Builds a pulse laser EngineerCraft record. {@code experimental} fills the module's current
     * ExperimentalEffect field; {@code apply} additionally sets ApplyExperimentalEffect (an
     * experimental application craft).
     */
    private static String pulseCraft(String slot, String blueprint, int level, String experimental, boolean apply) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":\"2026-07-20T10:00:00Z\",\"event\":\"EngineerCraft\",")
                .append("\"Slot\":\"").append(slot).append("\",")
                .append("\"Module\":\"hpt_pulselaser_gimbal_large\",")
                .append("\"Engineer\":\"Broo Tarquin\",\"EngineerID\":300030,")
                .append("\"BlueprintName\":\"").append(blueprint).append("\",")
                .append("\"Level\":").append(level).append(",\"Quality\":1.0");
        if (apply) {
            sb.append(",\"ApplyExperimentalEffect\":\"").append(experimental).append("\"");
        }
        if (experimental != null) {
            sb.append(",\"ExperimentalEffect\":\"").append(experimental).append("\"")
                    .append(",\"ExperimentalEffect_Localised\":\"Oversized\"");
        }
        return sb.append("}").toString();
    }

    @Test
    void completedGoal_dropsExperimentalMaterialsFromPlanner() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental",
                true,
                true);

        assertTrue(goal.isComplete());
        assertTrue(planner.materialsForGoal(goal).isEmpty());
        assertTrue(planner.isGoalReady(goal, Map.of()));
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, Map.of(), Map.of()));
    }

    @Test
    void incompleteExperimentalGoal_stillRequiresExperimentalMaterials() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        assertFalse(required.isEmpty());
        assertTrue(required.containsKey("wornshieldemitters"));
        assertFalse(goal.isComplete());
    }
}
