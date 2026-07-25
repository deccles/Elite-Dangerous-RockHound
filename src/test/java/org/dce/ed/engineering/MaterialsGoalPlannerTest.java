package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaterialsGoalPlannerTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;
    private static MaterialTradePlanner tradePlanner;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
        tradePlanner = new MaterialTradePlanner(db);
    }

    @Test
    void materialsGoal_reservesStackOnBlueprintNeeds() {
        BlueprintGrade g1 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        EngineeringGoal craft = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 1, "");
        Map<String, Integer> craftNeed = planner.materialsForGoal(craft);
        assertFalse(craftNeed.isEmpty());
        String key = craftNeed.keySet().iterator().next();
        int craftCount = craftNeed.get(key);

        MaterialsGoal reserve = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement(key, 7)),
                GoalPriority.MEDIUM);

        Map<String, Integer> combined = planner.requiredMaterials(List.of(craft), List.of(reserve));
        assertEquals(craftCount + 7, combined.get(key).intValue());
    }

    @Test
    void planByPriority_highMaterialsGoalClaimsBeforeLowCraft() {
        BlueprintGrade lowBp = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        EngineeringGoal lowCraft = new EngineeringGoal(
                lowBp.getId(), lowBp.getModuleType(), lowBp.getName(), 0, 0, 1, "",
                GoalPriority.LOW);
        Map<String, Integer> craftNeed = planner.materialsForGoal(lowCraft);
        assertFalse(craftNeed.isEmpty());
        String contested = craftNeed.keySet().iterator().next();
        int craftCount = craftNeed.get(contested);

        MaterialsGoal highReserve = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement(contested, craftCount)),
                GoalPriority.HIGH);

        // Exactly enough for the reserve OR the craft, not both.
        Map<String, Integer> inv = new HashMap<>();
        inv.put(contested, craftCount);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(lowCraft), List.of(highReserve), inv, tradePlanner);

        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(highReserve));
        assertEquals(GoalReadiness.STILL_SHORT, plan.readinessByBlueprintGoal().get(lowCraft));
    }

    @Test
    void materialsGoal_readiness_readyIsStockedNotCraftComplete() {
        MaterialsGoal goal = new MaterialsGoal(
                "Need iron",
                List.of(new MaterialRequirement("iron", 10)),
                GoalPriority.MEDIUM);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, Map.of(), Map.of()));
        // Stocked reserve is READY; the Engineering tab labels that "Ready" (not "Complete").
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, Map.of("iron", 10), Map.of("iron", 10)));
        assertEquals(
                GoalReadiness.READY_WITH_TRADES,
                planner.goalReadiness(goal, Map.of("iron", 0), Map.of("iron", 10)));
        assertTrue(planner.isGoalReady(goal, Map.of("iron", 10)));
        assertFalse(planner.isGoalReady(goal, Map.of("iron", 3)));
    }

    @Test
    void materialsGoal_acquisitionTarget_ownedPlusNeedCreatesShortfall() {
        // Mirrors Add-dialog semantics: Need=7 more while owning 5 → target 12 → shortfall 7.
        int owned = 5;
        int needMore = 7;
        int target = owned + needMore;
        MaterialsGoal goal = new MaterialsGoal(
                "Acquire iron",
                List.of(new MaterialRequirement("iron", target)),
                GoalPriority.MEDIUM);
        Map<String, Integer> inv = Map.of("iron", owned);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, inv, inv));
        assertEquals(needMore, goal.shortfalls(inv).get("iron").intValue());
    }

    @Test
    void planByPriority_doesNotTradeAwayStockReservedForLaterGoal() {
        // Repro: commander owns exactly the 5 Compound Shielding a later goal needs. The
        // higher-priority Heat Exchangers goal must not spend them (which used to produce a
        // circular "Need 0" buy-back suggestion for Compound Shielding).
        MaterialsGoal heatExchangers = new MaterialsGoal(
                "Heat exchangers",
                List.of(new MaterialRequirement("heatexchangers", 6)),
                GoalPriority.HIGH);
        MaterialsGoal compoundShielding = new MaterialsGoal(
                "Compound shielding",
                List.of(new MaterialRequirement("compoundshielding", 5)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of("compoundshielding", 5);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(heatExchangers, compoundShielding), inv, tradePlanner);

        assertTrue(plan.trades().stream().noneMatch(
                        t -> "compoundshielding".equalsIgnoreCase(t.getFromKey())),
                "must not spend Compound Shielding reserved for the later goal");
        assertTrue(plan.trades().stream().noneMatch(
                        t -> "compoundshielding".equalsIgnoreCase(t.getToKey())),
                "must not suggest buying back a material with no overall shortfall");
        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(compoundShielding));
    }

    @Test
    void planByPriority_tradeSubsetStillRespectsOtherGoalsReservations() {
        // Hide-other-ship UI: only suggest trades for the selected ship's goal, but still
        // reserve materials for every ship's goals so we never trade them away.
        MaterialsGoal otherShipReserve = new MaterialsGoal(
                "Other ship reserve",
                List.of(new MaterialRequirement("compoundshielding", 5)),
                GoalPriority.HIGH);
        MaterialsGoal selectedShipNeed = new MaterialsGoal(
                "Selected ship need",
                List.of(new MaterialRequirement("heatexchangers", 6)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of("compoundshielding", 5);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(),
                List.of(otherShipReserve, selectedShipNeed),
                inv,
                tradePlanner,
                List.of(),
                List.of(selectedShipNeed));

        assertTrue(plan.trades().stream().noneMatch(
                        t -> "compoundshielding".equalsIgnoreCase(t.getFromKey())),
                "ship-scoped trades must not spend stock reserved for other ships");
        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(otherShipReserve));
    }

    @Test
    void planByPriority_doesNotSpendPhaseAlloysOnlyEarnedByEarlierTradeSuggestion() {
        // Repro: Need 10 Phase Alloys filled by Proto→Phase (1→3) yields 12; leftover 2 used to
        // be spent on Conductive Components even though on-hand Phase Alloys is 0.
        MaterialsGoal needPhase = new MaterialsGoal(
                "Phase alloys",
                List.of(new MaterialRequirement("phasealloys", 10)),
                GoalPriority.HIGH);
        MaterialsGoal needConductive = new MaterialsGoal(
                "Conductive components",
                List.of(new MaterialRequirement("conductivecomponents", 1)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of(
                "protolightalloys", 10,
                "phasealloys", 0,
                "conductivecomponents", 0);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(needPhase, needConductive), inv, tradePlanner);

        assertTrue(plan.trades().stream().anyMatch(
                        t -> "phasealloys".equalsIgnoreCase(t.getToKey())),
                "should still suggest acquiring Phase Alloys for the high goal");
        assertTrue(plan.trades().stream().noneMatch(
                        t -> "phasealloys".equalsIgnoreCase(t.getFromKey())),
                "must not pay Phase Alloys the commander does not own yet");
        // Leftover Proto may still cover Conductive via a direct trade; the important
        // guard is that Phase Alloys themselves are never offered as pay stock.
        if (plan.readinessByMaterialsGoal().get(needConductive) == GoalReadiness.STILL_SHORT) {
            assertTrue(plan.shortfallsRemainingAfterPlan().containsKey("conductivecomponents")
                            || plan.shortfallsRemainingAfterPlan().keySet().stream()
                            .anyMatch(k -> "conductivecomponents".equalsIgnoreCase(k)),
                    "Short goals must expose remaining shortfalls for Trade Suggestions");
        }
    }

    @Test
    void planByPriority_doesNotPayWithMaterialsAlreadyClaimedForEarlierGoal() {
        // High goal claims all iron for crafting. Low goal must not spend that iron on a trade.
        MaterialsGoal needIron = new MaterialsGoal(
                "Iron reserve",
                List.of(new MaterialRequirement("iron", 10)),
                GoalPriority.HIGH);
        MaterialsGoal needCadmium = new MaterialsGoal(
                "Cadmium",
                List.of(new MaterialRequirement("cadmium", 6)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of("iron", 10, "cadmium", 0);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(needIron, needCadmium), inv, tradePlanner);

        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(needIron));
        assertEquals(GoalReadiness.STILL_SHORT, plan.readinessByMaterialsGoal().get(needCadmium));
        assertTrue(plan.trades().stream().noneMatch(
                        t -> "iron".equalsIgnoreCase(t.getFromKey())),
                "must not pay Iron already claimed by the higher-priority goal");
        assertFalse(plan.shortfallsRemainingAfterPlan().isEmpty());
    }

    @Test
    void planByPriority_stillShortGoalsReportRemainingShortfalls() {
        MaterialsGoal highReserve = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement("iron", 5)),
                GoalPriority.HIGH);
        MaterialsGoal lowReserve = new MaterialsGoal(
                "More iron",
                List.of(new MaterialRequirement("iron", 5)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of("iron", 5);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(highReserve, lowReserve), inv, tradePlanner);

        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(highReserve));
        assertEquals(GoalReadiness.STILL_SHORT, plan.readinessByMaterialsGoal().get(lowReserve));
        assertEquals(5, plan.shortfallsRemainingAfterPlan().getOrDefault("iron", 0).intValue());
    }

    @Test
    void materialsGoal_multiMaterialShortWhenAnyMissing() {
        MaterialsGoal goal = new MaterialsGoal(
                "Bundle",
                List.of(
                        new MaterialRequirement("iron", 5),
                        new MaterialRequirement("phosphorus", 3)),
                GoalPriority.MEDIUM);

        Map<String, Integer> partial = Map.of("iron", 5, "phosphorus", 0);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, partial, partial));

        Map<String, Integer> full = Map.of("iron", 5, "phosphorus", 3);
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, full, full));
        assertTrue(goal.isSatisfied(full));
    }
}
