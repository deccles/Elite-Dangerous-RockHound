package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.mission.MissionTracker;
import org.junit.jupiter.api.Test;

class CombatTabPanelMissionListTest {

    private final EliteLogParser parser = new EliteLogParser();

    /**
     * Exact {@code MissionAccepted} from Journal.2026-08-19T090207.01.log — the most recent
     * combat accept in the live journals.
     */
    private static final String JOURNAL_MASSACRE_WING =
            "{ \"timestamp\":\"2026-08-20T00:45:30Z\", \"event\":\"MissionAccepted\","
                    + " \"Faction\":\"Crimson Transport Ltd\","
                    + " \"Name\":\"Mission_MassacreWing_Legal_Military\","
                    + " \"LocalisedName\":\"Engage and destroy Brotherhood of Korro Kung Pirates\","
                    + " \"TargetType\":\"$MissionUtil_FactionTag_Pirate;\","
                    + " \"TargetType_Localised\":\"Pirates\","
                    + " \"TargetFaction\":\"Brotherhood of Korro Kung\", \"KillCount\":42,"
                    + " \"DestinationSystem\":\"Korro Kung\", \"DestinationStation\":\"Lonchakov Orbital\","
                    + " \"Expiry\":\"2026-08-27T00:26:44Z\", \"Wing\":true, \"Influence\":\"++\","
                    + " \"Reputation\":\"++\", \"Reward\":5068707, \"MissionID\":1063825808 }";

    @Test
    void liveJournalMassacreWingAccept_appearsOnCombatTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MissionsTabPanel missions = new MissionsTabPanel(
                    () -> false, () -> false, () -> "Korro Kung", () -> "Lonchakov Orbital");
            CombatTabPanel combat = new CombatTabPanel(() -> false);
            combat.setMissionTracker(missions.getTracker());

            var event = parser.parseRecord(JOURNAL_MASSACRE_WING);
            missions.handleLogEvent(event);
            combat.handleLogEvent(event);

            assertEquals(1, combat.combatMissionCount());
            assertEquals("Engage and destroy Brotherhood of Korro Kung Pirates",
                    combat.combatMissionNameAt(0));
            assertEquals("0/42", combat.combatMissionProgressAt(0));
            assertEquals("Brotherhood of Korro Kung", combat.combatMissionTargetAt(0));
        });
    }

    @Test
    void trackerAcceptWithoutCombatHandleLogEvent_stillFillsCombatTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MissionTracker tracker = new MissionTracker();
            CombatTabPanel combat = new CombatTabPanel(() -> false);
            combat.setMissionTracker(tracker);

            tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(JOURNAL_MASSACRE_WING));

            assertEquals(1, combat.combatMissionCount());
            assertEquals("Engage and destroy Brotherhood of Korro Kung Pirates",
                    combat.combatMissionNameAt(0));
        });
    }

    @Test
    void miningAccept_doesNotAppearOnCombatTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MissionsTabPanel missions = new MissionsTabPanel(
                    () -> false, () -> false, () -> "Colonia", () -> "Jaques Station");
            CombatTabPanel combat = new CombatTabPanel(() -> false);
            combat.setMissionTracker(missions.getTracker());

            var event = parser.parseRecord(
                    "{ \"timestamp\":\"2026-08-31T23:02:40Z\", \"event\":\"MissionAccepted\","
                            + " \"Faction\":\"People of Colonia\", \"Name\":\"Mission_Mining\","
                            + " \"LocalisedName\":\"Mine 118 Units of Bromellite\","
                            + " \"Commodity\":\"$Bromellite_Name;\", \"Commodity_Localised\":\"Bromellite\","
                            + " \"Count\":118, \"DestinationSystem\":\"Colonia\","
                            + " \"DestinationStation\":\"Jaques Station\", \"Reward\":8125800,"
                            + " \"MissionID\":1064876979 }");
            missions.handleLogEvent(event);
            combat.handleLogEvent(event);

            assertEquals(0, combat.combatMissionCount());
        });
    }
}
