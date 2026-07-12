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
}
