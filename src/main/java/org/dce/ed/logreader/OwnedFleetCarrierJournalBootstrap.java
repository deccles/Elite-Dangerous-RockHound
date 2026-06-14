package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.file.Path;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;

import com.google.gson.JsonObject;

/**
 * Replays recent journal events to learn the commander's owned fleet carrier id ({@code CarrierStats})
 * and last known location ({@code CarrierLocation} / owned {@code CarrierJump}).
 */
public final class OwnedFleetCarrierJournalBootstrap {

    private static final int JOURNAL_FILES_TO_SCAN = 60;

    private OwnedFleetCarrierJournalBootstrap() {
    }

    public static void replayInto(OwnedFleetCarrierTracker tracker) {
        if (tracker == null) {
            return;
        }
        try {
            Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
                return;
            }
            replayInto(tracker, journalDir);
        } catch (Exception ignored) {
        }
    }

    public static void replayInto(OwnedFleetCarrierTracker tracker, Path journalDir) throws IOException {
        if (tracker == null || journalDir == null) {
            return;
        }
        EliteJournalReader reader = new EliteJournalReader(journalDir);
        java.util.List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(JOURNAL_FILES_TO_SCAN);
        for (EliteLogEvent event : events) {
            if (event != null && event.getType() == EliteEventType.CARRIER_STATS) {
                tracker.onCarrierStats(OwnedFleetCarrierTracker.carrierIdFromJson(event.getRawJson()));
            }
        }
        boolean pendingOwnedJump = false;
        for (EliteLogEvent event : events) {
            if (event == null) {
                continue;
            }
            if (event.getType() == EliteEventType.CARRIER_STATS) {
                continue;
            }
            if (event instanceof CarrierJumpRequestEvent req) {
                tracker.onCarrierJumpRequest(req);
                if (tracker.isOwnedCarrierId(req.getCarrierId())) {
                    pendingOwnedJump = true;
                }
            } else if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
                pendingOwnedJump = false;
            } else if (event instanceof CarrierLocationEvent loc) {
                tracker.onCarrierLocation(loc);
            } else if (event instanceof CarrierJumpEvent jump) {
                if (tracker.isOwnedCarrierJump(jump, pendingOwnedJump)) {
                    tracker.onOwnedCarrierJumpCompleted(jump);
                    pendingOwnedJump = false;
                }
            } else if (event.getType() == EliteEventType.DOCKED) {
                applyDockedFleetCarrier(tracker, event.getRawJson());
            }
        }
    }

    private static void applyDockedFleetCarrier(OwnedFleetCarrierTracker tracker, JsonObject raw) {
        if (raw == null) {
            return;
        }
        String stationType = raw.has("StationType") && !raw.get("StationType").isJsonNull()
                ? raw.get("StationType").getAsString()
                : null;
        if (stationType == null || !"FleetCarrier".equalsIgnoreCase(stationType.trim())) {
            return;
        }
        long systemAddress = raw.has("SystemAddress") && !raw.get("SystemAddress").isJsonNull()
                ? raw.get("SystemAddress").getAsLong()
                : 0L;
        String starSystem = raw.has("StarSystem") && !raw.get("StarSystem").isJsonNull()
                ? raw.get("StarSystem").getAsString()
                : null;
        tracker.onDockedFleetCarrier(
                OwnedFleetCarrierTracker.marketIdFromDockedJson(raw),
                starSystem,
                systemAddress);
    }
}
