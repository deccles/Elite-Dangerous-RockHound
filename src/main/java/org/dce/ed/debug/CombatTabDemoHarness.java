package org.dce.ed.debug;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.dce.ed.CombatTabPanel;
import org.dce.ed.CombatTargetTracker;
import org.dce.ed.EdoTestFlags;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.mission.MissionTracker;
import org.dce.ed.ui.EdoUi;

import com.google.gson.JsonParser;

/**
 * Standalone demo for the Combat tab with canned journal data.
 * <p>
 * Isolation: sets {@link EdoTestFlags#ISOLATE_UI_PROPERTY} and {@code edo.test.disableSpeech}
 * before any UI work, never writes {@link org.dce.ed.OverlayPreferences}, session state, or mining
 * DBs. Seeded combat/mission state lives only in this JVM's memory and is cleared on close.
 * <p>
 * Run from the project root (PowerShell — quote the {@code -D} property):
 * <pre>
 *   mvn -q -DskipTests "-Dexec.mainClass=org.dce.ed.debug.CombatTabDemoHarness" exec:java
 * </pre>
 * Or run {@link #main} from the IDE.
 */
public final class CombatTabDemoHarness {

    private static final EliteLogParser PARSER = new EliteLogParser();
    private static final String DEMO_SYSTEM = "Demo Conflict Zone";

    private CombatTabDemoHarness() {
    }

    public static void main(String[] args) {
        // Before any OverlayPreferences / TTS / session code can run.
        System.setProperty(EdoTestFlags.ISOLATE_UI_PROPERTY, "true");
        System.setProperty("edo.test.disableSpeech", "true");

        SwingUtilities.invokeLater(CombatTabDemoHarness::createAndShow);
    }

    private static void createAndShow() {
        CombatTargetTracker.getInstance().resetForTests();

        seedExampleData();

        MissionTracker missions = new MissionTracker();
        missions.setCurrentSystemSupplier(() -> DEMO_SYSTEM);
        seedMissions(missions);

        CombatTabPanel combatTab = new CombatTabPanel(() -> false);
        combatTab.setMissionTracker(missions);
        combatTab.setUnclaimedBountyCreditsSupplier(() -> 3_425_319L);
        combatTab.reloadCombatCommandBindings();
        combatTab.setBackground(EdoUi.User.BACKGROUND);
        combatTab.setOpaque(true);

        JFrame frame = new JFrame("EDO Combat Tab Demo (read-only prefs)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(EdoUi.User.BACKGROUND);
        frame.setLayout(new BorderLayout());

        frame.add(combatTab, BorderLayout.CENTER);
        frame.setSize(new Dimension(720, 820));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // In-memory only — never persisted.
                CombatTargetTracker.getInstance().resetForTests();
            }
        });
        frame.setVisible(true);
    }

    private static void seedExampleData() {
        CombatTargetTracker tracker = CombatTargetTracker.getInstance();

        // Scanned wanted — first sighting only (Remote = ?)
        tracker.applyJournalEvent(shipTargeted(
                "Peter Brooke",
                "$npc_name_decorate:#name=Peter Brooke;",
                "cobramkiv",
                "Cobra Mk IV",
                "Wanted",
                284_400L,
                false));

        // Scanned + KWS with no additional remote (Remote = 0)
        tracker.applyJournalEvent(shipTargeted(
                "Clean Sweep",
                "$npc_name_decorate:#name=Clean Sweep;",
                "viper",
                "Viper Mk III",
                "Wanted",
                95_000L,
                false));
        tracker.applyJournalEvent(shipTargeted(
                "Clean Sweep",
                "$npc_name_decorate:#name=Clean Sweep;",
                "viper",
                "Viper Mk III",
                "Wanted",
                95_000L,
                false));

        // Scanned + KWS with remote bounty — also becomes current TARGET (Hostile = red)
        tracker.applyJournalEvent(shipTargeted(
                "Rexford",
                "$npc_name_decorate:#name=Rexford;",
                "asp",
                "Asp Scout",
                "Hostile",
                231_695L,
                false));
        tracker.applyJournalEvent(shipTargeted(
                "Rexford",
                "$npc_name_decorate:#name=Rexford;",
                "asp",
                "Asp Scout",
                "Hostile",
                365_571L,
                false));

        // Player pilot (blue) — warrant not scanned
        tracker.applyJournalEvent(shipTargeted(
                "CMDR Example",
                "CMDR Example",
                "ferdelance",
                "Fer-de-Lance",
                "Wanted",
                512_000L,
                true));
        // Re-lock Rexford so TARGET shows the hostile with remote
        tracker.applyJournalEvent(shipTargeted(
                "Rexford",
                "$npc_name_decorate:#name=Rexford;",
                "asp",
                "Asp Scout",
                "Hostile",
                365_571L,
                false));

        tracker.applyJournalEvent(bounty("cobramkiv", "Kacomam Mob", 284_800L,
                "[{\"Faction\":\"Kacomam Mob\",\"Reward\":284800}]"));
        tracker.applyJournalEvent(bounty("asp_scout", "Alliance Office of Statistics", 365_571L,
                "[{\"Faction\":\"Alliance Office of Statistics\",\"Reward\":231695},"
                        + "{\"Faction\":\"Remote Warrant Brokers\",\"Reward\":133876}]"));
        tracker.applyJournalEvent(bounty("eagle", "Terran Colonial Forces", 71_295L,
                "[{\"Faction\":\"Terran Colonial Forces\",\"Reward\":71295}]"));
    }

    private static void seedMissions(MissionTracker missions) {
        missions.applyEvent(new MissionAcceptedEvent(Instant.now(), JsonParser.parseString(
                "{"
                        + "\"event\":\"MissionAccepted\","
                        + "\"MissionID\":1001,"
                        + "\"Name\":\"Mission_Massacre\","
                        + "\"LocalisedName\":\"Massacre the Kacomam Mob\","
                        + "\"TargetFaction\":\"Kacomam Mob\","
                        + "\"KillCount\":12,"
                        + "\"DestinationSystem\":\"" + DEMO_SYSTEM + "\","
                        + "\"Reward\":1850000"
                        + "}").getAsJsonObject()));

        missions.applyEvent(new MissionAcceptedEvent(Instant.now(), JsonParser.parseString(
                "{"
                        + "\"event\":\"MissionAccepted\","
                        + "\"MissionID\":1002,"
                        + "\"Name\":\"Mission_Assassinate\","
                        + "\"LocalisedName\":\"Assassinate Rexford\","
                        + "\"Target\":\"Rexford\","
                        + "\"TargetFaction\":\"Hostile Pirates\","
                        + "\"KillCount\":1,"
                        + "\"DestinationSystem\":\"" + DEMO_SYSTEM + "\","
                        + "\"Reward\":450000"
                        + "}").getAsJsonObject()));

        // Advance massacre progress with matching bounty kills
        for (int i = 0; i < 4; i++) {
            missions.applyEvent(bounty("sidewinder", "Kacomam Mob", 12_000L,
                    "[{\"Faction\":\"Local Security\",\"Reward\":12000}]"));
        }
    }

    private static org.dce.ed.logreader.event.ShipTargetedEvent shipTargeted(
            String pilotLocalised,
            String rawPilot,
            String ship,
            String shipLocalised,
            String legal,
            long bounty,
            boolean player) {
        String line = "{"
                + "\"timestamp\":\"2026-07-28T12:00:00Z\","
                + "\"event\":\"ShipTargeted\","
                + "\"TargetLocked\":true,"
                + "\"Ship\":\"" + ship + "\","
                + "\"Ship_Localised\":\"" + shipLocalised + "\","
                + "\"ScanStage\":3,"
                + "\"PilotName\":\"" + escapeJson(rawPilot) + "\","
                + "\"PilotName_Localised\":\"" + escapeJson(pilotLocalised) + "\","
                + "\"PilotRank\":\"Dangerous\","
                + "\"Faction\":\"Demo Faction\","
                + "\"LegalStatus\":\"" + legal + "\","
                + "\"Bounty\":" + bounty
                + (player ? ",\"SquadronID\":\"DEMO\"" : "")
                + "}";
        return (org.dce.ed.logreader.event.ShipTargetedEvent) PARSER.parseRecord(line);
    }

    private static BountyEvent bounty(String target, String victimFaction, long total, String rewardsJson) {
        String line = "{"
                + "\"timestamp\":\"2026-07-28T12:05:00Z\","
                + "\"event\":\"Bounty\","
                + "\"Rewards\":" + rewardsJson + ","
                + "\"Target\":\"" + target + "\","
                + "\"TotalReward\":" + total + ","
                + "\"VictimFaction\":\"" + victimFaction + "\""
                + "}";
        return (BountyEvent) PARSER.parseRecord(line);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
