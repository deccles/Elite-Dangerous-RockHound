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

class EngineeringGoalProgressCraftSyncTest {

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void applyCraft_advancesProgressEvenWhenIngredientCountsDifferFromCatalog() {
        // Journal uses names that resolve, but omit a catalog ingredient — must still advance.
        String json = """
                {
                  "timestamp": "2026-07-13T20:00:00Z",
                  "event": "EngineerCraft",
                  "Slot": "PowerDistributor",
                  "Module": "int_powerdistributor_size8_class5",
                  "Ingredients": [
                    {
                      "Name": "legacyfirmware",
                      "Name_Localised": "Specialised Legacy Firmware",
                      "Count": 1
                    }
                  ],
                  "Engineer": "The Dweller",
                  "BlueprintName": "PowerDistributor_HighFrequency",
                  "Level": 1,
                  "Quality": 0.2
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertEquals(0, goals.get(0).getFromGrade());
        assertEquals(1, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void hasMatchingGoal_distinguishesWakeScannerFromKillWarrantScanner() {
        String json = """
                {
                  "timestamp": "2026-07-31T21:03:54Z",
                  "event": "EngineerCraft",
                  "Slot": "TinyHardpoint7",
                  "Module": "hpt_cloudscanner_size0_class5",
                  "BlueprintName": "Sensor_FastScan",
                  "Level": 5,
                  "Quality": 1.0,
                  "Ingredients": []
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "kill-warrant-scanner-fast-scanner-g5",
                "Kill Warrant Scanner",
                "Fast Scanner",
                0,
                0,
                5,
                null));

        assertFalse(EngineeringGoalProgress.hasMatchingGoal(goals, craft, db, 7L));

        goals.add(new EngineeringGoal(
                "wake-scanner-fast-scanner-g5",
                "Wake Scanner",
                "Fast Scanner",
                0,
                0,
                5,
                null));
        assertTrue(EngineeringGoalProgress.hasMatchingGoal(goals, craft, db, 7L));
    }

    @Test
    void applyLoadout_usesQualityForInProgressGradeNotCompletedLevel() {
        String json = """
                {
                  "timestamp": "2026-07-13T20:05:00Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 1,
                  "Modules": [
                    {
                      "Slot": "PowerDistributor",
                      "Item": "int_powerdistributor_size8_class5",
                      "On": true,
                      "Priority": 0,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "The Dweller",
                        "BlueprintName": "PowerDistributor_HighFrequency",
                        "Level": 2,
                        "Quality": 0.4
                      }
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        // Level 2 @ 40% quality ⇒ G1 complete, 2/5 rolls into G2 — not G2 complete.
        assertEquals(1, goals.get(0).getFromGrade());
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void applyLoadout_doesNotRegressCraftTrackingAheadOfLoadout() {
        String json = """
                {
                  "timestamp": "2026-07-13T20:06:00Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 1,
                  "Modules": [
                    {
                      "Slot": "PowerDistributor",
                      "Item": "int_powerdistributor_size8_class5",
                      "On": true,
                      "Priority": 0,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "The Dweller",
                        "BlueprintName": "PowerDistributor_HighFrequency",
                        "Level": 2,
                        "Quality": 0.4
                      }
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                1,
                3,
                5,
                null));

        EngineeringGoalProgress.applyLoadout(goals, loadout, db);
        assertEquals(1, goals.get(0).getFromGrade());
        assertEquals(3, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void applyLoadout_doesNotWipeJournalProgressWhenModulesAreOnAnotherShip() {
        // Current ship has no matching engineering — must not clear craft/unit progress
        // recovered from journal history for modules on other ships.
        String json = """
                {
                  "timestamp": "2026-07-14T12:00:00Z",
                  "event": "Loadout",
                  "Ship": "sidewinder",
                  "ShipID": 2,
                  "Modules": [
                    {
                      "Slot": "TinyHardpoint1",
                      "Item": "hpt_pulselaser_fixed_small",
                      "On": true,
                      "Priority": 0,
                      "Health": 1.0
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-booster-heavy-duty",
                "Shield Booster",
                "Heavy Duty",
                4,
                2,
                5,
                null,
                true,
                false,
                4,
                3));

        EngineeringGoalProgress.applyLoadout(goals, loadout, db);
        assertEquals(3, goals.get(0).getCompletedUnits());
        assertEquals(4, goals.get(0).getFromGrade());
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void applyCraft_advancesMultiCannonFromWeaponOverchargedJournalName() {
        String json = """
                {
                  "timestamp": "2026-07-14T00:35:22Z",
                  "event": "EngineerCraft",
                  "Slot": "SmallHardpoint2",
                  "Module": "hpt_multicannon_turret_small",
                  "Ingredients": [
                    { "Name": "carbon", "Count": 1 }
                  ],
                  "Engineer": "Tod 'The Blaster' McQuinn",
                  "BlueprintName": "Weapon_Overcharged",
                  "Level": 1,
                  "Quality": 0.2
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "multi-cannon-overcharged-weapon-g5",
                "Multi-cannon",
                "Overcharged Weapon",
                0,
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertEquals(0, goals.get(0).getFromGrade());
        assertEquals(1, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void replayCraftHistory_countsInterleavedSlotsForQuantity() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-booster-heavy-duty",
                "Shield Booster",
                "Heavy Duty",
                0,
                0,
                1,
                null,
                true,
                false,
                2,
                0);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(goal);

        List<EngineerCraftEvent> crafts = new ArrayList<>();
        // Interleave: five L1 rolls on slot A, five L1 rolls on slot B (completes target G1 for both).
        for (int i = 0; i < 5; i++) {
            crafts.add(boosterCraft("TinyHardpoint1", 1));
            crafts.add(boosterCraft("TinyHardpoint2", 1));
        }

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, crafts, db));
        assertEquals(2, goals.get(0).getCompletedUnits());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void replayCraftHistory_advancesSeparateWeaponGoals() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "mc-oc",
                "Multi-cannon",
                "Overcharged Weapon",
                0,
                0,
                5,
                null));
        goals.add(new EngineeringGoal(
                "pulse-eff",
                "Pulse Laser",
                "Efficient Weapon",
                0,
                0,
                5,
                null));

        List<EngineerCraftEvent> crafts = List.of(
                (EngineerCraftEvent) parser.parseRecord("""
                        {
                          "timestamp": "2026-07-14T00:35:22Z",
                          "event": "EngineerCraft",
                          "Slot": "SmallHardpoint2",
                          "Module": "hpt_multicannon_turret_small",
                          "Ingredients": [{ "Name": "carbon", "Count": 1 }],
                          "Engineer": "Tod",
                          "BlueprintName": "Weapon_Overcharged",
                          "Level": 1,
                          "Quality": 0.2
                        }
                        """),
                (EngineerCraftEvent) parser.parseRecord("""
                        {
                          "timestamp": "2026-07-14T00:36:22Z",
                          "event": "EngineerCraft",
                          "Slot": "SmallHardpoint1",
                          "Module": "hpt_pulselaser_gimbal_small",
                          "Ingredients": [{ "Name": "carbon", "Count": 1 }],
                          "Engineer": "Tod",
                          "BlueprintName": "Weapon_Efficient",
                          "Level": 1,
                          "Quality": 0.2
                        }
                        """));

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, crafts, db));
        assertEquals(1, goals.get(0).getCraftsAtCurrentGrade());
        assertEquals(1, goals.get(1).getCraftsAtCurrentGrade());
    }

    @Test
    void replay_qualityFinishesGradeEarly_loweringTargetMarksComplete() {
        // Real Marco Qwent session: G2 in two rolls, G3 in three, then Super Conduits — while the
        // goal still targeted G5. Lowering to G3 must become Complete (not Ready for a phantom roll).
        List<org.dce.ed.logreader.EliteLogEvent> events = new ArrayList<>();
        events.add(pdCraft(1, 0.5));
        events.add(pdCraft(1, 1.0));
        events.add(pdCraft(2, 0.5));
        events.add(pdCraft(2, 1.0));
        events.add(pdCraft(3, 0.3333));
        events.add(pdCraft(3, 0.6667));
        events.add(pdCraft(3, 1.0));
        events.add(parser.parseRecord("""
                {
                  "timestamp": "2026-07-22T13:14:09Z",
                  "event": "EngineerCraft",
                  "Slot": "PowerDistributor",
                  "Module": "int_powerdistributor_size6_class5",
                  "Engineer": "Marco Qwent",
                  "BlueprintName": "PowerDistributor_HighFrequency",
                  "Level": 3,
                  "Quality": 1.0,
                  "ApplyExperimentalEffect": "special_powerdistributor_fast",
                  "ExperimentalEffect": "special_powerdistributor_fast",
                  "ExperimentalEffect_Localised": "Super Conduits",
                  "Ingredients": [
                    { "Name": "phosphorus", "Count": 5 },
                    { "Name": "heatresistantceramics", "Count": 3 },
                    { "Name": "securityfirmwarepatch", "Count": 1 }
                  ]
                }
                """));

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                "power-distributor-super-conduits-experimental"));

        assertTrue(EngineeringGoalProgress.replayCraftHistory(goals, events, db));
        EngineeringGoal atG5 = goals.get(0);
        assertEquals(3, atG5.getFromGrade());
        assertTrue(atG5.isExperimentalApplied());
        assertTrue(!atG5.isComplete(), "still targeting G5");

        EngineeringGoal lowered = atG5.withUserSettings(3, atG5.getExperimentalId(), 1);
        assertEquals(3, lowered.getTargetGrade());
        assertEquals(3, lowered.getFromGrade());
        assertTrue(lowered.isExperimentalApplied());
        assertTrue(lowered.isComplete(), "G3 + experimental must be Complete after lowering target");

        // Re-bootstrap as the edit dialog does: reset + replay against the new target.
        List<EngineeringGoal> refreshed = new ArrayList<>();
        refreshed.add(lowered.resetJournalProgress());
        assertTrue(EngineeringGoalProgress.replayCraftHistory(refreshed, events, db));
        assertTrue(refreshed.get(0).isComplete());
    }

    private EngineerCraftEvent pdCraft(int level, double quality) {
        return (EngineerCraftEvent) parser.parseRecord("""
                {
                  "timestamp": "2026-07-22T13:13:00Z",
                  "event": "EngineerCraft",
                  "Slot": "PowerDistributor",
                  "Module": "int_powerdistributor_size6_class5",
                  "Ingredients": [{ "Name": "legacyfirmware", "Count": 1 }],
                  "Engineer": "Marco Qwent",
                  "BlueprintName": "PowerDistributor_HighFrequency",
                  "Level": %d,
                  "Quality": %s
                }
                """.formatted(Integer.valueOf(level), Double.valueOf(quality)));
    }

    private EngineerCraftEvent boosterCraft(String slot, int level) {
        return (EngineerCraftEvent) parser.parseRecord("""
                {
                  "timestamp": "2026-07-14T01:00:00Z",
                  "event": "EngineerCraft",
                  "Slot": "%s",
                  "Module": "hpt_shieldbooster_size0_class5",
                  "Ingredients": [{ "Name": "iron", "Count": 1 }],
                  "Engineer": "Felicity Farseer",
                  "BlueprintName": "ShieldBooster_HeavyDuty",
                  "Level": %d,
                  "Quality": 0.2
                }
                """.formatted(slot, Integer.valueOf(level)));
    }
}
