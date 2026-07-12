package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HighChargeCapacityMaterialsTest {

    @Test
    void g1Goal_includesSulphur() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        var g1 = db.gradesFor("Power Distributor", "High Charge Capacity").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        assertTrue(g1.getMaterials().stream().anyMatch(m -> "sulphur".equals(m.getKey())));

        EngineeringGoal goal = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 1, "");
        EngineeringPlanner planner = new EngineeringPlanner(db);
        List<ShoppingListRow> rows = planner.buildShoppingList(List.of(goal), Map.of());
        assertTrue(rows.stream().anyMatch(r -> "sulphur".equals(r.getMaterialKey())),
                "shopping list should include sulphur: " + rows);
    }
}
