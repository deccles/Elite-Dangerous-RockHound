package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.ModuleRetrieveEvent;
import org.dce.ed.logreader.event.ModuleStoreEvent;
import org.junit.jupiter.api.Test;

class EngineeringLoadoutSlotPatchTest {

    private static final EliteLogParser PARSER = new EliteLogParser();

    private static final String LOADOUT_G4_LS = """
            {
              "timestamp": "2026-09-04T05:00:00Z",
              "event": "Loadout",
              "Ship": "federation_corvette",
              "ShipID": 23,
              "Modules": [
                {
                  "Slot": "LifeSupport",
                  "Item": "int_lifesupport_size3_class2",
                  "On": true,
                  "Priority": 0,
                  "Engineering": {
                    "BlueprintName": "Misc_Lightweight",
                    "Level": 4,
                    "Quality": 1.0,
                    "ExperimentalEffect": "special_lightweight"
                  }
                }
              ]
            }
            """;

    private static final String RETRIEVE_STOCK = """
            {
              "timestamp": "2026-09-04T05:01:00Z",
              "event": "ModuleRetrieve",
              "MarketID": 1,
              "Slot": "LifeSupport",
              "Ship": "federation_corvette",
              "ShipID": 23,
              "RetrievedItem": "$int_lifesupport_size3_class1_name;",
              "RetrievedItem_Localised": "Life Support",
              "SwapOutItem": "$int_lifesupport_size3_class2_name;",
              "Hot": false
            }
            """;

    private static final String RETRIEVE_G4 = """
            {
              "timestamp": "2026-09-04T05:02:00Z",
              "event": "ModuleRetrieve",
              "MarketID": 1,
              "Slot": "LifeSupport",
              "Ship": "federation_corvette",
              "ShipID": 23,
              "RetrievedItem": "$int_lifesupport_size3_class2_name;",
              "RetrievedItem_Localised": "Life Support",
              "EngineerModifications": "Misc_Lightweight",
              "Level": 4,
              "Quality": 1.0,
              "Hot": false
            }
            """;

    private static final String STORE_CORE_STOCK_FILL = """
            {
              "timestamp": "2026-09-04T05:03:00Z",
              "event": "ModuleStore",
              "MarketID": 1,
              "Slot": "LifeSupport",
              "Ship": "federation_corvette",
              "ShipID": 23,
              "StoredItem": "$int_lifesupport_size3_class2_name;",
              "EngineerModifications": "Misc_Lightweight",
              "Level": 4,
              "Quality": 1.0,
              "ReplacementItem": "$int_lifesupport_size3_class1_name;",
              "Hot": false
            }
            """;

    @Test
    void toLoadoutItemId_stripsJournalLocalisationWrapper() {
        assertEquals("int_lifesupport_size3_class2",
                EngineeringLoadoutSlotPatch.toLoadoutItemId("$int_lifesupport_size3_class2_name;"));
        assertEquals("int_lifesupport_size3_class2",
                EngineeringLoadoutSlotPatch.toLoadoutItemId("int_lifesupport_size3_class2"));
    }

    @Test
    void retrieveStock_clearsEngineeringAndSwapsItem() {
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(RETRIEVE_STOCK);
        String patched = EngineeringLoadoutSlotPatch.patchRetrieve(LOADOUT_G4_LS, retrieve);
        assertNotNull(patched);
        LoadoutEvent loadout = (LoadoutEvent) PARSER.parseRecord(patched);
        LoadoutEvent.Module ls = onlyLifeSupport(loadout);
        assertEquals("int_lifesupport_size3_class1", ls.getItem());
        assertNull(ls.getEngineering());
        assertEquals("2026-09-04T05:01:00Z", loadout.getTimestamp().toString());
    }

    @Test
    void retrieveEngineered_setsLevelAndDropsStaleExperimental() {
        String stockLoadout = """
                {
                  "timestamp": "2026-09-04T05:00:00Z",
                  "event": "Loadout",
                  "Ship": "federation_corvette",
                  "ShipID": 23,
                  "Modules": [
                    { "Slot": "LifeSupport", "Item": "int_lifesupport_size3_class1", "On": true }
                  ]
                }
                """;
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(RETRIEVE_G4);
        String patched = EngineeringLoadoutSlotPatch.patchRetrieve(stockLoadout, retrieve);
        assertNotNull(patched);
        LoadoutEvent.Module ls = onlyLifeSupport((LoadoutEvent) PARSER.parseRecord(patched));
        assertEquals("int_lifesupport_size3_class2", ls.getItem());
        assertNotNull(ls.getEngineering());
        assertEquals("Misc_Lightweight", ls.getEngineering().getBlueprintName());
        assertEquals(4, ls.getEngineering().getLevel());
        assertEquals(1.0, ls.getEngineering().getQuality(), 1e-9);
        assertTrue(ls.getEngineering().getExperimentalEffect() == null
                || ls.getEngineering().getExperimentalEffect().isBlank());
    }

    @Test
    void storeCore_replacesSlotWithStockFiller() {
        ModuleStoreEvent store = (ModuleStoreEvent) PARSER.parseRecord(STORE_CORE_STOCK_FILL);
        String patched = EngineeringLoadoutSlotPatch.patchStore(LOADOUT_G4_LS, store);
        assertNotNull(patched);
        LoadoutEvent.Module ls = onlyLifeSupport((LoadoutEvent) PARSER.parseRecord(patched));
        assertEquals("int_lifesupport_size3_class1", ls.getItem());
        assertNull(ls.getEngineering());
    }

    @Test
    void retrieveStock_resetsQty1GoalNeedImmediately() {
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(RETRIEVE_STOCK);
        String patched = EngineeringLoadoutSlotPatch.patchRetrieve(LOADOUT_G4_LS, retrieve);
        LoadoutEvent loadout = (LoadoutEvent) PARSER.parseRecord(patched);
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "life-support-lightweight-g5",
                "Life Support",
                "Lightweight",
                4,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                23L,
                "Federal Corvette",
                true,
                "LifeSupport"));
        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db, true));
        EngineeringGoal goal = goals.get(0);
        assertEquals(0, goal.getFromGrade());
        assertFalse(goal.isComplete());
        Map<String, Integer> need = new EngineeringPlanner(db).materialsForGoal(goal);
        assertTrue(need.getOrDefault("phosphorus", 0) >= 1, "G0 LS must request G1 materials: " + need);
    }

    @Test
    void retrieveSameItemWithoutLevel_treatsAsStockUntilLoadout() {
        String retrieveSameClass2 = """
                {
                  "timestamp": "2026-09-04T05:52:08Z",
                  "event": "ModuleRetrieve",
                  "Slot": "LifeSupport",
                  "Ship": "federation_corvette",
                  "ShipID": 23,
                  "RetrievedItem": "$int_lifesupport_size3_class2_name;",
                  "SwapOutItem": "$int_lifesupport_size3_class2_name;",
                  "Hot": false
                }
                """;
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(retrieveSameClass2);
        String patched = EngineeringLoadoutSlotPatch.patchRetrieve(LOADOUT_G4_LS, retrieve);
        assertNotNull(patched);
        LoadoutEvent.Module ls = onlyLifeSupport((LoadoutEvent) PARSER.parseRecord(patched));
        assertEquals("int_lifesupport_size3_class2", ls.getItem());
        assertNull(ls.getEngineering());
    }

    @Test
    void retrieveSameItemWithoutLevel_resetsQty1GoalNeed() {
        String retrieveSameClass2 = """
                {
                  "timestamp": "2026-09-04T05:52:08Z",
                  "event": "ModuleRetrieve",
                  "Slot": "LifeSupport",
                  "Ship": "federation_corvette",
                  "ShipID": 23,
                  "RetrievedItem": "$int_lifesupport_size3_class2_name;",
                  "Hot": false
                }
                """;
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(retrieveSameClass2);
        String patched = EngineeringLoadoutSlotPatch.patchRetrieve(LOADOUT_G4_LS, retrieve);
        LoadoutEvent loadout = (LoadoutEvent) PARSER.parseRecord(patched);
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "life-support-lightweight-g5",
                "Life Support",
                "Lightweight",
                4,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                23L,
                "Federal Corvette",
                true,
                "LifeSupport"));
        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db, true));
        assertEquals(0, goals.get(0).getFromGrade());
    }

    @Test
    void wrongShipId_isNoOp() {
        String otherShip = LOADOUT_G4_LS.replace("\"ShipID\": 23", "\"ShipID\": 99");
        ModuleRetrieveEvent retrieve = (ModuleRetrieveEvent) PARSER.parseRecord(RETRIEVE_STOCK);
        assertNull(EngineeringLoadoutSlotPatch.patchRetrieve(otherShip, retrieve));
    }

    private static LoadoutEvent.Module onlyLifeSupport(LoadoutEvent loadout) {
        return loadout.getModules().stream()
                .filter(m -> "LifeSupport".equals(m.getSlot()))
                .findFirst()
                .orElseThrow();
    }
}
