package org.dce.ed.exobiology;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.state.BodyInfo;

/**
 * Re-applies genus-switch parking from journal {@code ScanOrganic} order so abandoned sample pins
 * survive overlay/game restarts even when live replay lacked commander lat/lon.
 */
public final class BioGenusSwitchRestorer {

    private BioGenusSwitchRestorer() {
    }

    public static boolean replayFromJournal(BodyInfo body) {
        if (body == null) {
            return false;
        }
        Map<String, List<BodyInfo.BioSamplePoint>> before = body.getAbandonedBioSamplePointsSnapshot();
        String activeBefore = body.getActiveIncompleteBioKey();
        if (!OverlayPreferences.isJournalDirectoryAvailable(EliteDangerousOverlay.clientKey)) {
            return false;
        }
        try {
            Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            EliteJournalReader reader = new EliteJournalReader(dir);
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(8);
            List<BodyInfo.BioScanReplayEntry> entries = new ArrayList<>();
            for (EliteLogEvent raw : events) {
                if (!(raw instanceof ScanOrganicEvent)) {
                    continue;
                }
                ScanOrganicEvent so = (ScanOrganicEvent) raw;
                if (!matchesBody(so, body)) {
                    continue;
                }
                String dn = displayNameFromScanOrganic(so);
                if (dn == null || dn.isBlank()) {
                    continue;
                }
                entries.add(new BodyInfo.BioScanReplayEntry(dn, so.getScanType()));
            }
            if (entries.isEmpty()) {
                return false;
            }
            body.replayGenusSwitchParkingFromJournal(entries);
            Map<String, List<BodyInfo.BioSamplePoint>> after = body.getAbandonedBioSamplePointsSnapshot();
            return !before.equals(after)
                    || !Objects.equals(activeBefore, body.getActiveIncompleteBioKey());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean matchesBody(ScanOrganicEvent so, BodyInfo body) {
        int bodyId = body.getBodyId();
        if (bodyId > 0 && so.getBodyId() == bodyId) {
            return true;
        }
        String bodyName = body.getBodyName();
        String eventName = so.getBodyName();
        return bodyName != null && !bodyName.isBlank()
                && eventName != null && !eventName.isBlank()
                && bodyName.equalsIgnoreCase(eventName);
    }

    private static String displayNameFromScanOrganic(ScanOrganicEvent e) {
        if (e == null) {
            return null;
        }
        String genusName = firstNonBlank(e.getGenusLocalised(), e.getGenus());
        String speciesName = firstNonBlank(e.getSpeciesLocalised(), e.getSpecies());
        if (genusName.isEmpty()) {
            return null;
        }
        if (speciesName.startsWith(genusName + " ")) {
            speciesName = speciesName.replace(genusName, "").trim();
        }
        if (!speciesName.isEmpty()) {
            return genusName + " " + speciesName;
        }
        return genusName;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "";
    }
}
