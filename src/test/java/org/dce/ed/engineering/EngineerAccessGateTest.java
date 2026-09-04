package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.dce.ed.logreader.event.EngineerProgressEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineerAccessGateTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void specialisedG4_melGrade2_locksG4() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-cell-bank-specialised-g4",
                "Shield Cell Bank",
                "Specialised",
                2,
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
        EngineerReputationTracker ranks = ranksOf(
                engineer("Mel Brandon", 2),
                engineer("Lori Jameson", 5));

        Optional<EngineerAccessGate.Block> block = EngineerAccessGate.blockingGrade(goal, db, ranks);

        assertTrue(block.isPresent());
        assertEquals(4, block.get().grade());
        assertEquals("Locked G4", block.get().summary());
        assertTrue(block.get().detail().contains("Mel Brandon"));
        assertTrue(block.get().detail().contains("Grade 2"));
        assertTrue(block.get().detail().contains(
                "Add a goal that engineers from G0 to G3 to account for the additional materials"));
        assertFalse(block.get().detail().contains("throwaway"));
        assertFalse(block.get().detail().contains("Outfitting"));
        assertFalse(block.get().detail().contains("wipes"));
        assertFalse(block.get().detail().contains("Lori Jameson"), block.get().detail());
        assertTrue(block.get().detail().contains("\n\n"), block.get().detail());
        String html = EngineerAccessGate.htmlTooltip(block.get().detail());
        assertTrue(html.contains("width:300px"), html);
        assertTrue(html.contains("<br>"), html);
        assertTrue(html.contains("edo-access-tip"), html);
    }

    @Test
    void footnote_oneEngineer_staysGeneric() {
        assertEquals("* This engineer may need higher grade access first",
                EngineerAccessGate.footnote(List.of("Etienne Dorn")));
        assertEquals("* This engineer may need higher grade access first",
                EngineerAccessGate.footnote(List.of()));
    }

    @Test
    void footnote_twoEngineers_namesBoth() {
        assertEquals("* Etienne Dorn and Mel Brandon may need higher grade access first",
                EngineerAccessGate.footnote(List.of("Etienne Dorn", "Mel Brandon")));
    }

    @Test
    void footnote_threeEngineers_usesAnd() {
        assertEquals("* Etienne Dorn, Mel Brandon, and Lori Jameson may need higher grade access first",
                EngineerAccessGate.footnote(List.of("Etienne Dorn", "Mel Brandon", "Lori Jameson")));
    }

    @Test
    void unengineeredSpecialised_melGrade2_notBlocked() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-cell-bank-specialised-g4",
                "Shield Cell Bank",
                "Specialised",
                0,
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
        EngineerReputationTracker ranks = ranksOf(engineer("Mel Brandon", 2));

        assertFalse(EngineerAccessGate.blockingGrade(goal, db, ranks).isPresent());
    }

    @Test
    void unengineeredSensors_etienneGrade1_notBlocked() {
        EngineeringGoal goal = new EngineeringGoal(
                "sensors-light-weight-scanner-g5",
                "Sensors",
                "Light Weight Scanner",
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                12L,
                "Type-11 Prospector",
                true);
        EngineerReputationTracker ranks = ranksOf(engineer("Etienne Dorn", 1));

        assertFalse(EngineerAccessGate.blockingGrade(goal, db, ranks).isPresent());
    }

    @Test
    void partialSensors_etienneGrade1_locked() {
        EngineeringGoal goal = new EngineeringGoal(
                "sensors-light-weight-scanner-g5",
                "Sensors",
                "Light Weight Scanner",
                3,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                12L,
                "Type-11 Prospector",
                true);
        EngineerReputationTracker ranks = ranksOf(engineer("Etienne Dorn", 1));

        Optional<EngineerAccessGate.Block> block = EngineerAccessGate.blockingGrade(goal, db, ranks);
        assertTrue(block.isPresent());
        assertEquals(4, block.get().grade());
        assertTrue(block.get().detail().contains(
                "Add a goal that engineers from G0 to G3 to account for the additional materials"));
    }

    @Test
    void specialisedG3_loriHasAccess_notBlocked() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-cell-bank-specialised-g3",
                "Shield Cell Bank",
                "Specialised",
                2,
                0,
                3,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                23L,
                "Federal Corvette",
                true);
        EngineerReputationTracker ranks = ranksOf(engineer("Lori Jameson", 5));

        assertFalse(EngineerAccessGate.blockingGrade(goal, db, ranks).isPresent());
    }

    @Test
    void completeGoal_notBlocked() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-cell-bank-specialised-g4",
                "Shield Cell Bank",
                "Specialised",
                4,
                0,
                4,
                "shield-cell-bank-boss-cells-experimental",
                GoalPriority.MEDIUM,
                true,
                1,
                1,
                23L,
                "Federal Corvette",
                true);
        EngineerReputationTracker ranks = ranksOf(engineer("Mel Brandon", 1));

        assertFalse(EngineerAccessGate.blockingGrade(goal, db, ranks).isPresent());
    }

    private static EngineerReputationTracker ranksOf(EngineerProgressEvent.EngineerRank... entries) {
        EngineerReputationTracker tracker = new EngineerReputationTracker();
        tracker.applyEvent(new EngineerProgressEvent(
                Instant.parse("2026-09-01T00:00:00Z"),
                new com.google.gson.JsonObject(),
                List.of(entries),
                false));
        return tracker;
    }

    private static EngineerProgressEvent.EngineerRank engineer(String name, int rank) {
        return new EngineerProgressEvent.EngineerRank(name, 1, "Unlocked", rank, 0);
    }
}
