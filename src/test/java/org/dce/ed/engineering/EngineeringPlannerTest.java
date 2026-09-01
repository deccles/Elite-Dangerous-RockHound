package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringPlannerTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        assertTrue(db.getAllBlueprints().size() > 100);
    }

    @Test
    void chargeEnhancedG5_superConduits_shortfallIncludesFirmware() {
        BlueprintGrade g5 = db.getAllBlueprints().stream()
                .filter(b -> "Power Distributor".equals(b.getModuleType()))
                .filter(b -> "Charge Enhanced".equals(b.getName()))
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();

        BlueprintGrade superConduits = db.getAllBlueprints().stream()
                .filter(b -> "Super Conduits".equals(b.getName()))
                .filter(BlueprintGrade::isExperimental)
                .findFirst()
                .orElseThrow();

        EngineeringGoal goal = new EngineeringGoal(
                g5.getId(),
                g5.getModuleType(),
                g5.getName(),
                0,
                5,
                superConduits.getId());

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> empty = Map.of();
        List<ShoppingListRow> rows = planner.buildShoppingList(List.of(goal), empty);

        int firmwareNeed = rows.stream()
                .filter(r -> r.getMaterialKey().contains("specialisedlegacy"))
                .mapToInt(ShoppingListRow::getRequired)
                .sum();
        assertTrue(firmwareNeed >= 10, "expected at least 10 specialised legacy firmware (5 rolls per grade)");

        int phosphorus = rows.stream()
                .filter(r -> "phosphorus".equals(r.getMaterialKey()))
                .mapToInt(ShoppingListRow::getRequired)
                .findFirst()
                .orElse(0);
        assertEquals(5, phosphorus);
    }

    @Test
    void multiUnitHrp_afterFinishingOneAtGrade_siblingsStillNeedFullGradeMats() {
        // Repro: qty 4 HRPs — finishing the first to G5 (experimental pending) used to cost only
        // Deep Plating for the other three, so Materials Required looked covered while the game
        // still needed G1–G5 mats for each remaining package.
        EngineeringGoal goal = new EngineeringGoal(
                "hull-reinforcement-package-heavy-duty-hull-reinforcement-g5",
                "Hull Reinforcement Package",
                "Heavy Duty Hull Reinforcement",
                5,
                0,
                5,
                "hull-reinforcement-package-deep-plating-experimental",
                GoalPriority.MEDIUM,
                false,
                4,
                1,
                7L,
                "Anaconda",
                true);

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);

        // remainingUnits = 3 (1 in-progress at G5 needing exp + 2 not started).
        // Current: Deep Plating only. Two siblings: full G0→G5 + exp.
        assertEquals(5 + 5 * 2, need.getOrDefault("compactcomposites", 0).intValue());
        // Carbon appears on G1–G3 (5 rolls each) → 15 per full sibling unit.
        assertEquals(15 * 2, need.getOrDefault("carbon", 0).intValue(),
                "sibling HRPs must still need full grade materials, not experimental-only");
        assertEquals(5 * 2, need.getOrDefault("tungsten", 0).intValue());
    }

    @Test
    void multiUnit_currentUnitCompleteButQuantityRemains_stillNeedsFullSiblingMats() {
        // Repro: Resistance Augmented qty 3 with completedUnits=1 and current sitting at
        // G5+experimental (Ready) reported Need=0 while two boosters were still unfinished.
        EngineeringGoal goal = new EngineeringGoal(
                "shield-booster-resistance-augmented-g5",
                "Shield Booster",
                "Resistance Augmented",
                5,
                0,
                5,
                "shield-booster-super-capacitor-experimental",
                GoalPriority.MEDIUM,
                true,
                3,
                1,
                7L,
                "Anaconda",
                true);
        assertTrue(goal.isCurrentUnitComplete());
        assertEquals(1, goal.remainingUnits());

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);
        assertTrue(need.getOrDefault("refinedfocuscrystals", 0) > 0,
                "must still budget grade mats for remaining units: " + need);
        assertTrue(need.getOrDefault("manganese", 0) > 0 || need.getOrDefault("phosphorus", 0) > 0,
                "must include early-grade mats for a fresh sibling unit: " + need);
    }

    @Test
    void multiUnit_allAtGradeNoCompletions_experimentalOnlyForEveryUnit() {
        // Multi-hardpoint case: nothing completed yet, shared progress already at target grade —
        // remaining units are experimental applies only.
        EngineeringGoal goal = new EngineeringGoal(
                "hull-reinforcement-package-heavy-duty-hull-reinforcement-g5",
                "Hull Reinforcement Package",
                "Heavy Duty Hull Reinforcement",
                5,
                0,
                5,
                "hull-reinforcement-package-deep-plating-experimental",
                GoalPriority.MEDIUM,
                false,
                4,
                0,
                7L,
                "Anaconda",
                true);

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);

        assertEquals(5 * 4, need.getOrDefault("compactcomposites", 0).intValue());
        assertEquals(0, need.getOrDefault("carbon", 0).intValue(),
                "grade-complete batch should not re-buy G1–G5 mats");
    }

    @Test
    void multiUnitScb_bothAtG3WithBossCells_g4DoesNotRebuyExperimental() {
        // Repro: Corvette 2× 7A Specialised G3 + Boss Cells targeting G4. After restart, Need
        // treated the sibling as stock and asked for Chemical Storage Units (Boss Cells) again.
        EngineeringGoal goal = new EngineeringGoal(
                "shield-cell-bank-specialised-g4",
                "Shield Cell Bank",
                "Specialised",
                3,
                0,
                4,
                "shield-cell-bank-boss-cells-experimental",
                GoalPriority.MEDIUM,
                true,
                2,
                0,
                23L,
                "Federal Corvette",
                true);

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);

        assertEquals(0, need.getOrDefault("chemicalstorageunits", 0).intValue(),
                "Boss Cells already on both cells must not reappear in Need: " + need);
        assertTrue(need.getOrDefault("yttrium", 0) > 0 || need.getOrDefault("crackedindustrialfirmware", 0) > 0,
                "G4 Specialised mats must still be needed: " + need);
    }
}
