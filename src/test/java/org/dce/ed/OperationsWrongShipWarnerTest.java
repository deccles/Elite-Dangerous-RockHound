package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsWrongShipWarnerTest {

    private final EliteLogParser parser = new EliteLogParser();
    private boolean previousEnabled;
    private List<String> previousShips;

    @BeforeEach
    void setUp() {
        previousEnabled = OverlayPreferences.isOperationsWrongShipWarnEnabled();
        previousShips = OverlayPreferences.getOperationsPreferredShips();
        OverlayPreferences.setOperationsWrongShipWarnEnabled(true);
        OverlayPreferences.setOperationsPreferredShips(List.of("Federal Corvette"));
        OperationsWrongShipWarner.getInstance().setWarningCallback(null);
        OperationsWrongShipWarner.getInstance().resetState();
    }

    @AfterEach
    void tearDown() {
        OverlayPreferences.setOperationsWrongShipWarnEnabled(previousEnabled);
        OverlayPreferences.setOperationsPreferredShips(previousShips);
        OperationsWrongShipWarner.getInstance().setWarningCallback(null);
        OperationsWrongShipWarner.getInstance().resetState();
    }

    @Test
    void rightPanelThenWingJoinInPantherWarns() {
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        assertFalse(w.applyJournalEvent(parse(status(1))));
        assertFalse(w.applyJournalEvent(parse(status(0))));
        assertFalse(w.applyJournalEvent(parse(loadGame("PantherMkII"))));
        assertTrue(w.applyJournalEvent(parse(wingJoin())));
        assertEquals(1, w.savedNonZeroGuiFocus());
    }

    @Test
    void rightPanelThenWingJoinInCorvetteDoesNotWarn() {
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(status(0)));
        w.applyJournalEvent(parse(loadGame("federation_corvette")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
    }

    @Test
    void commsThenWingJoinDoesNotWarn() {
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(3)));
        w.applyJournalEvent(parse(loadGame("PantherMkII")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
        assertEquals(3, w.savedNonZeroGuiFocus());
    }

    @Test
    void secondWingJoinInSameLobbyDoesNotWarnAgain() {
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(status(0)));
        w.applyJournalEvent(parse(loadGame("PantherMkII")));
        assertTrue(w.applyJournalEvent(parse(wingJoin())));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
    }

    @Test
    void disabledPrefDoesNotWarn() {
        OverlayPreferences.setOperationsWrongShipWarnEnabled(false);
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("PantherMkII")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
    }

    @Test
    void anacondaPrefMatchesAnacondaJournalId() {
        OverlayPreferences.setOperationsPreferredShips(List.of("Anaconda"));
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("anaconda")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
        w.resetState();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("PantherMkII")));
        assertTrue(w.applyJournalEvent(parse(wingJoin())));
    }

    @Test
    void anyShipInTheListIsAccepted() {
        OverlayPreferences.setOperationsPreferredShips(List.of("Anaconda", "Dolphin"));
        OperationsWrongShipWarner w = OperationsWrongShipWarner.getInstance();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("dolphin")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
        w.resetState();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("anaconda")));
        assertFalse(w.applyJournalEvent(parse(wingJoin())));
        w.resetState();
        w.applyJournalEvent(parse(status(1)));
        w.applyJournalEvent(parse(loadGame("PantherMkII")));
        assertTrue(w.applyJournalEvent(parse(wingJoin())));
    }

    @Test
    void statusMessageNamesTheExpectedShip() {
        assertEquals("Wrong ship for Operations, expected \"Anaconda\"",
                OperationsWrongShipWarner.wrongShipStatusMessage(List.of("Anaconda")));
    }

    @Test
    void statusMessageJoinsSeveralShips() {
        assertEquals("Wrong ship for Operations, expected \"Anaconda\" or \"Dolphin\"",
                OperationsWrongShipWarner.wrongShipStatusMessage(List.of("Anaconda", "Dolphin")));
        assertEquals("Wrong ship for Operations, expected \"Anaconda\", \"Dolphin\" or \"Orca\"",
                OperationsWrongShipWarner.wrongShipStatusMessage(List.of("Anaconda", "Dolphin", "Orca")));
    }

    @Test
    void legacySingleShipPreferenceStillLoads() {
        assertEquals(List.of("Federal Corvette"),
                OverlayPreferences.splitOperationsPreferredShips("Federal Corvette"));
        assertEquals(List.of("Anaconda", "Dolphin"),
                OverlayPreferences.splitOperationsPreferredShips("Anaconda|Dolphin"));
    }

    @Test
    void wingJoinParsesAsWingJoinType() {
        EliteLogEvent event = parse(wingJoin());
        assertEquals(EliteEventType.WING_JOIN, event.getType());
        assertTrue(OperationsWrongShipWarner.isWingJoin(event));
    }

    private EliteLogEvent parse(String line) {
        return parser.parseRecord(line);
    }

    private static String status(int guiFocus) {
        return "{ \"timestamp\":\"2026-09-10T14:00:00Z\", \"event\":\"Status\", \"GuiFocus\":" + guiFocus + " }";
    }

    private static String loadGame(String ship) {
        return "{ \"timestamp\":\"2026-09-10T14:00:00Z\", \"event\":\"LoadGame\", \"Ship\":\"" + ship + "\" }";
    }

    private static String wingJoin() {
        return "{ \"timestamp\":\"2026-09-10T14:00:05Z\", \"event\":\"WingJoin\", \"Others\":[ \"ZEPHYRNL\" ] }";
    }
}
