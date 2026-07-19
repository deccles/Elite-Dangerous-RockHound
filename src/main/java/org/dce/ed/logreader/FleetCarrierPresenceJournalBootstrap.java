package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;

/**
 * Replays recent journal presence events so {@link FleetCarrierCommanderPresence} knows whether the
 * commander is aboard their fleet carrier at startup. {@link LiveJournalMonitor} resumes from a tail
 * cursor and never re-dispatches history, so the Docked / Location event that put the commander on the
 * carrier is otherwise lost across an EDO restart.
 */
public final class FleetCarrierPresenceJournalBootstrap {

    private static final int JOURNAL_FILES_TO_SCAN = 60;

    /** Journal event names that change aboard-carrier presence (cheap pre-parse filter). */
    private static final Set<String> PRESENCE_EVENT_NAMES =
            Set.of("Location", "Docked", "Undocked", "FSDJump", "CarrierJump");

    private FleetCarrierPresenceJournalBootstrap() {
    }

    public static void replayInto(FleetCarrierCommanderPresence presence) {
        if (presence == null) {
            return;
        }
        try {
            Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
                return;
            }
            replayInto(presence, journalDir);
        } catch (Exception ignored) {
        }
    }

    public static void replayInto(FleetCarrierCommanderPresence presence, Path journalDir) throws IOException {
        if (presence == null || journalDir == null) {
            return;
        }
        EliteJournalReader reader = new EliteJournalReader(journalDir);
        List<EliteLogEvent> events =
                reader.readEventsFromLastNJournalFiles(JOURNAL_FILES_TO_SCAN, PRESENCE_EVENT_NAMES);
        for (EliteLogEvent event : events) {
            presence.applyJournalEvent(event);
        }
    }
}
