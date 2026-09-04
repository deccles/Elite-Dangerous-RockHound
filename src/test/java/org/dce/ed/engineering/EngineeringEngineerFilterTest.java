package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringEngineerFilterTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void catalogIncludesKnownEngineers() {
        assertTrue(db.catalogEngineerNames().contains("Etienne Dorn"));
        assertTrue(db.catalogEngineerNames().contains("Felicity Farseer"));
        assertTrue(db.catalogEngineerNames().contains("Mel Brandon"));
        assertFalse(db.catalogEngineerNames().stream().anyMatch(n -> n.startsWith("@")));
    }

    @Test
    void anyFilter_matchesEverything() {
        EngineeringGoal sensors = goal("Sensors", "Light Weight Scanner", 0, 5);
        assertTrue(db.engineerCanWorkGoal(sensors, null));
        assertTrue(db.engineerCanWorkGoal(sensors, "Any"));
        assertTrue(db.engineerOffersModuleType("Frame Shift Drive", "any"));
    }

    @Test
    void etienneCanWorkFreshSensors_notFsd() {
        EngineeringGoal sensors = goal("Sensors", "Light Weight Scanner", 0, 5);
        EngineeringGoal fsd = goal("Frame Shift Drive", "Increased FSD Range", 0, 5);
        assertTrue(db.engineerCanWorkGoal(sensors, "Etienne Dorn"));
        assertFalse(db.engineerCanWorkGoal(fsd, "Etienne Dorn"));
        assertTrue(db.engineerOffersModuleType("Sensors", "Etienne Dorn"));
        assertFalse(db.engineerOffersModuleType("Frame Shift Drive", "Etienne Dorn"));
    }

    @Test
    void loriCannotContinueSpecialisedG4() {
        EngineeringGoal remainingG4 = goal("Shield Cell Bank", "Specialised", 3, 4);
        EngineeringGoal fresh = goal("Shield Cell Bank", "Specialised", 0, 4);
        assertFalse(db.engineerCanWorkGoal(remainingG4, "Lori Jameson"));
        assertTrue(db.engineerCanWorkGoal(remainingG4, "Mel Brandon"));
        assertTrue(db.engineerCanWorkGoal(fresh, "Lori Jameson"));
    }

    @Test
    void loadoutSummary_dropsModulesEngineerCannotDo() {
        String loadoutJson =
                "{\"timestamp\":\"2026-07-23T04:16:12Z\",\"event\":\"Loadout\",\"Ship\":\"anaconda\",\"ShipID\":7,"
                        + "\"Modules\":["
                        + "{\"Slot\":\"FrameShiftDrive\",\"Item\":\"int_hyperdrive_size6_class5\",\"On\":true},"
                        + "{\"Slot\":\"Radar\",\"Item\":\"int_sensors_size8_class2\",\"On\":true}"
                        + "]}";
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(loadoutJson);
        ShipEngineeringSummary all = ShipEngineeringSummary.fromLoadout(loadout, db);
        assertTrue(all.rows().stream().anyMatch(r -> r.moduleType().toLowerCase().contains("frame shift")));
        assertTrue(all.rows().stream().anyMatch(r -> "Sensors".equalsIgnoreCase(r.moduleType())));

        ShipEngineeringSummary etienne = all.excluding(
                row -> !db.engineerOffersModuleType(row.moduleType(), "Etienne Dorn"));
        assertFalse(etienne.rows().stream().anyMatch(r -> r.moduleType().toLowerCase().contains("frame shift")));
        assertTrue(etienne.rows().stream().anyMatch(r -> "Sensors".equalsIgnoreCase(r.moduleType())));
        assertTrue(etienne.withoutOtherModules().otherModules().isEmpty());
    }

    private static EngineeringGoal goal(String module, String blueprint, int from, int target) {
        return new EngineeringGoal(
                module.toLowerCase().replace(' ', '-') + "-test",
                module,
                blueprint,
                from,
                0,
                target,
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
