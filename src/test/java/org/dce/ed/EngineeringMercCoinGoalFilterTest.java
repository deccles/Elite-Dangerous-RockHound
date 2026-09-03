package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.GoalPriority;
import org.dce.ed.engineering.ShipEngineeringSummary;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.Test;

class EngineeringMercCoinGoalFilterTest {

    @Test
    void mercCoinRecipe_hiddenWhenIncludeIsOff() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        EngineeringGoal scoop = goal("Fuel Scoop", "Scoop Rate Enhanced");
        EngineeringGoal fsd = goal("Frame Shift Drive", "Increased FSD Range");

        assertTrue(EngineeringTabPanel.isMercCoinGoalIncluded(scoop, db, true));
        assertFalse(EngineeringTabPanel.isMercCoinGoalIncluded(scoop, db, false));
        assertTrue(EngineeringTabPanel.isMercCoinGoalIncluded(fsd, db, false));
    }

    @Test
    void mercCoinOnlyModuleTypes_areCargoRackNotFuelScoop() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        assertTrue(db.moduleHasOnlyMercCoinBlueprints("Cargo Rack"));
        assertTrue(db.moduleHasOnlyMercCoinBlueprints("Abrasion Blaster"));
        assertTrue(db.moduleHasOnlyMercCoinBlueprints("Mining Laser"));
        assertTrue(db.moduleHasOnlyMercCoinBlueprints("Enzyme Missile Rack"));
        assertTrue(db.moduleHasOnlyMercCoinBlueprints("Module Reinforcement Package"));
        assertFalse(db.moduleHasOnlyMercCoinBlueprints("Fuel Scoop"));
        assertFalse(db.moduleHasOnlyMercCoinBlueprints("Frame Shift Drive"));
        assertFalse(db.moduleHasOnlyMercCoinBlueprints("Power Distributor"));
        assertFalse(db.moduleHasOnlyMercCoinBlueprints("Beam Laser"));
        assertFalse(db.moduleHasOnlyMercCoinBlueprints("Surface Scanner"));
    }

    @Test
    void loadoutSummary_canDropMercCoinOnlyModules() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        String loadoutJson =
                "{\"timestamp\":\"2026-07-23T04:16:12Z\",\"event\":\"Loadout\",\"Ship\":\"anaconda\",\"ShipID\":7,"
                        + "\"Modules\":["
                        + "{\"Slot\":\"FrameShiftDrive\",\"Item\":\"int_hyperdrive_size6_class5\",\"On\":true},"
                        + "{\"Slot\":\"Slot06_Size5\",\"Item\":\"int_cargorack_size5_class1\",\"On\":true}"
                        + "]}";
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(loadoutJson);
        ShipEngineeringSummary all = ShipEngineeringSummary.fromLoadout(loadout, db);
        assertTrue(all.rows().stream().anyMatch(r -> "Cargo Rack".equalsIgnoreCase(r.moduleType())));
        ShipEngineeringSummary filtered = all.excluding(
                row -> db.moduleHasOnlyMercCoinBlueprints(row.moduleType()));
        assertFalse(filtered.rows().stream().anyMatch(r -> "Cargo Rack".equalsIgnoreCase(r.moduleType())));
        assertTrue(filtered.rows().stream().anyMatch(r -> r.moduleType().toLowerCase().contains("frame shift")));
    }

    private static EngineeringGoal goal(String moduleType, String blueprintName) {
        return new EngineeringGoal(
                "test",
                moduleType,
                blueprintName,
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                1L,
                "Test",
                true);
    }
}
