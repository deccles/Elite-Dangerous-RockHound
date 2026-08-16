package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.mission.MissionRecord;
import org.junit.jupiter.api.Test;

class MissionsTabPanelCombatBondTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void liveFactionKillBondReachesMissionTracker() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false,
                () -> false,
                () -> "Gliese 868",
                () -> null);
        panel.handleLogEvent(parser.parseRecord(
                "{\"timestamp\":\"2026-08-13T19:51:59Z\",\"event\":\"MissionAccepted\","
                        + "\"MissionID\":1063278166,\"Name\":\"Mission_Massacre_Conflict_War\","
                        + "\"TargetFaction\":\"Union of Gliese 868 Green Party\",\"KillCount\":36,"
                        + "\"DestinationSystem\":\"Gliese 868\"}"));

        panel.handleLogEvent(parser.parseRecord(
                "{\"timestamp\":\"2026-08-13T20:02:14Z\",\"event\":\"FactionKillBond\","
                        + "\"Reward\":41881,\"VictimFaction\":\"Union of Gliese 868 Green Party\"}"));

        MissionRecord mission = panel.getTracker().findById(1063278166L);
        assertEquals(1, mission.getKillsCompleted());
    }
}
