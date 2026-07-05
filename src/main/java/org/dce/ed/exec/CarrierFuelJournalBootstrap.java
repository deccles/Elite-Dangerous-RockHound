package org.dce.ed.exec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;

import com.google.gson.JsonObject;

/**
 * Replays recent {@code CarrierStats} journal events so {@link CarrierFuelTracker} has a fuel level
 * before the player opens carrier management again this session.
 */
public final class CarrierFuelJournalBootstrap {

    private static final int JOURNAL_FILES_TO_SCAN = 60;

    private CarrierFuelJournalBootstrap() {
    }

    public static void replayInto(CarrierFuelTracker fuelTracker, OwnedFleetCarrierTracker ownedTracker) {
        if (fuelTracker == null) {
            return;
        }
        try {
            Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
                return;
            }
            replayInto(fuelTracker, ownedTracker, journalDir);
        } catch (Exception ignored) {
        }
    }

    public static void replayInto(CarrierFuelTracker fuelTracker, OwnedFleetCarrierTracker ownedTracker,
            Path journalDir) throws IOException {
        if (fuelTracker == null || journalDir == null) {
            return;
        }
        EliteJournalReader reader = new EliteJournalReader(journalDir);
        List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(JOURNAL_FILES_TO_SCAN);
        long ownedId = ownedTracker != null ? ownedTracker.getOwnedCarrierId() : 0L;
        JsonObject lastMatchingStats = null;
        for (EliteLogEvent event : events) {
            if (event == null || event.getType() != EliteEventType.CARRIER_STATS) {
                continue;
            }
            JsonObject raw = event.getRawJson();
            if (raw == null) {
                continue;
            }
            long carrierId = CarrierFuelTracker.fuelCarrierIdFromJson(raw);
            if (carrierId == 0L) {
                continue;
            }
            if (ownedId != 0L && carrierId != ownedId) {
                continue;
            }
            lastMatchingStats = raw;
        }
        if (lastMatchingStats != null) {
            fuelTracker.ingestCarrierStats(lastMatchingStats, ownedId);
        }
    }
}
