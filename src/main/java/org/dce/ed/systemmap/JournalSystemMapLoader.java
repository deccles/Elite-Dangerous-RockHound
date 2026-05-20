package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.ScanBaryCentreEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.JournalParentRefs;
import org.dce.ed.state.ScanParents;
import org.dce.ed.state.SystemState;

/**
 * Replays journal {@code Scan} and {@code ScanBaryCentre} events for one system into a {@link SystemState}.
 */
public final class JournalSystemMapLoader {

    private JournalSystemMapLoader() {
    }

    /**
     * Default Elite journal directory ({@code USERPROFILE} on Windows, {@code HOME} elsewhere).
     */
    public static Path defaultJournalDirectory() {
        String home = System.getenv("USERPROFILE");
        if (home == null || home.isBlank()) {
            home = System.getenv("HOME");
        }
        if (home != null && !home.isBlank()) {
            return Path.of(home, "Saved Games", "Frontier Developments", "Elite Dangerous");
        }
        return Path.of("Saved Games", "Frontier Developments", "Elite Dangerous");
    }

    public static SystemState loadFromJournal(Path journalDirectory, String systemName) throws IOException {
        if (journalDirectory == null || !Files.isDirectory(journalDirectory)) {
            throw new IOException("Journal directory not found: " + journalDirectory);
        }
        EliteJournalReader reader = new EliteJournalReader(journalDirectory);
        SystemState state = new SystemState();
        String target = systemName.trim().toUpperCase(Locale.ROOT);
        for (EliteLogEvent event : reader.readAllEvents()) {
            String sys = null;
            if (event instanceof ScanEvent scan) {
                sys = scan.getStarSystem();
            } else if (event instanceof ScanBaryCentreEvent bary) {
                sys = bary.getStarSystem();
            } else {
                continue;
            }
            if (sys == null || !sys.trim().toUpperCase(Locale.ROOT).equals(target)) {
                continue;
            }
            if (event instanceof ScanEvent scan) {
                applyScan(state, scan);
            } else {
                applyScanBaryCentre(state, (ScanBaryCentreEvent) event);
            }
        }
        if (state.getBodies().isEmpty()) {
            throw new IOException("No Scan events found for system: " + systemName);
        }
        return state;
    }

    public static void applyScanBaryCentre(SystemState state, ScanBaryCentreEvent e) {
        if (e.getBodyId() < 0) {
            return;
        }
        state.setSystemName(e.getStarSystem());
        state.setSystemAddress(e.getSystemAddress());
        BodyInfo info = state.getOrCreateBody(e.getBodyId());
        info.setBodyId(e.getBodyId());
        info.setStarSystem(e.getStarSystem());
        if (e.getStarSystem() != null && !e.getStarSystem().isBlank()) {
            info.setBodyName(e.getStarSystem() + " barycentre " + e.getBodyId());
            info.setBodyShortName("bary " + e.getBodyId());
        }
        info.setScanBarycentreRow(true);
        info.setOrbitalPeriod(e.getOrbitalPeriod());
        if (e.getSemiMajorAxisM() != null) {
            info.setSemiMajorAxisM(e.getSemiMajorAxisM());
        }
    }

    public static void applyScan(SystemState state, ScanEvent e) {
        if (e.getBodyName() != null && e.getBodyName().contains("Belt")) {
            return;
        }
        state.setSystemName(e.getStarSystem());
        state.setSystemAddress(e.getSystemAddress());
        int key = e.getBodyId() >= 0 ? e.getBodyId() : stableTempKey(e.getBodyName());
        BodyInfo info = state.getOrCreateBody(key);
        info.setBodyId(key);
        info.setBodyName(e.getBodyName());
        info.setStarSystem(e.getStarSystem());
        info.setBodyShortName(state.computeShortName(e.getStarSystem(), e.getBodyName()));
        info.setDistanceLs(e.getDistanceFromArrivalLs());
        info.setPlanetClass(e.getPlanetClass());
        info.setAtmosphere(e.getAtmosphere());
        info.setAtmoOrType(e.getAtmosphere() != null ? e.getAtmosphere() : e.getPlanetClass());
        if (e.getStarType() != null) {
            info.setStarType(e.getStarType());
        }
        if (e.getSemiMajorAxisM() != null) {
            info.setSemiMajorAxisM(e.getSemiMajorAxisM());
        }
        int ip = ScanParents.immediateOrbitParentBodyId(e.getParents(), e);
        if (ip >= 0) {
            info.setImmediateParentBodyId(ip);
        }
        if (e.getParents() != null && !e.getParents().isEmpty()) {
            info.setJournalParentRefs(JournalParentRefs.fromScanParents(e.getParents()));
        }
    }

    private static int stableTempKey(String bodyName) {
        return -Math.abs(bodyName.hashCode());
    }
}
