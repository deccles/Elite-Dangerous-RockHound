package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.ScanParents;
import org.dce.ed.state.SystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Replays journal {@code Scan} events for one system and writes a {@link SystemMapFixture} JSON file. Intended for
 * developers refreshing fixtures from a real journal folder.
 * <p>
 * Run manually, e.g.:
 * {@code mvn -q test -Dtest=SystemMapJournalExtractorTest#exportSystem -Dedo.journal.dir="%USERPROFILE%\\Saved Games\\Frontier Developments\\Elite Dangerous" -Dedo.export.system="Byua Aim TT-X c15-29"}
 */
public final class SystemMapJournalExtractor {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SystemMapJournalExtractor() {
    }

    /**
     * @return fixture built from the most recent scans of {@code systemName} found in {@code journalDirectory}
     */
    public static SystemMapFixture extractFromJournals(Path journalDirectory, String systemName) throws IOException {
        if (journalDirectory == null || !Files.isDirectory(journalDirectory)) {
            throw new IOException("Journal directory not found: " + journalDirectory);
        }
        EliteJournalReader reader = new EliteJournalReader(journalDirectory);
        SystemState state = new SystemState();
        String target = systemName.trim().toUpperCase(Locale.ROOT);
        for (EliteLogEvent event : reader.readAllEvents()) {
            if (!(event instanceof ScanEvent scan)) {
                continue;
            }
            String sys = scan.getStarSystem();
            if (sys == null || !sys.trim().toUpperCase(Locale.ROOT).equals(target)) {
                continue;
            }
            applyScan(state, scan);
        }
        if (state.getBodies().isEmpty()) {
            throw new IOException("No Scan events found for system: " + systemName);
        }
        return toFixture(state);
    }

    public static void writeFixture(SystemMapFixture fixture, Path out) throws IOException {
        Files.writeString(out, GSON.toJson(fixture));
    }

    private static void applyScan(SystemState state, ScanEvent e) {
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
        List<ScanEvent.ParentRef> parents = e.getParents();
        int ip = ScanParents.immediateOrbitParentBodyId(parents, e);
        if (ip >= 0) {
            info.setImmediateParentBodyId(ip);
        }
    }

    private static int stableTempKey(String bodyName) {
        return -Math.abs(bodyName.hashCode());
    }

    private static SystemMapFixture toFixture(SystemState state) {
        SystemMapFixture fx = new SystemMapFixture();
        fx.name = state.getSystemName();
        fx.notes = "Auto-exported from journal Scan events";
        fx.bodies = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : state.getBodies().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            SystemMapFixture.BodySpec spec = new SystemMapFixture.BodySpec();
            spec.id = e.getKey().intValue();
            spec.bodyName = b.getBodyName();
            spec.shortName = b.getShortName();
            spec.distanceLs = b.getDistanceLs();
            spec.starType = b.getStarType();
            spec.planetClass = b.getPlanetClass();
            spec.atmoOrType = b.getAtmoOrType();
            int p = b.getImmediateParentBodyId();
            if (p == 0 && !state.getBodies().containsKey(Integer.valueOf(0))
                    && SystemMapRules.isMapStellarBody(b)) {
                spec.parentIsBarycentre = Boolean.TRUE;
            } else if (p >= 0) {
                spec.immediateParentBodyId = Integer.valueOf(p);
            }
            if (b.getSemiMajorAxisM() != null) {
                spec.semiMajorAxisM = b.getSemiMajorAxisM();
            }
            fx.bodies.add(spec);
        }
        SystemMapModel model = SystemMapPipeline.build(fx.name, state.getBodies(), java.time.Instant.EPOCH, true);
        fx.expect = new SystemMapFixture.Expect();
        fx.expect.layoutKind = model.classification().layoutKind().name();
        fx.expect.mapStellarCount = Integer.valueOf(model.classification().mapStellarCount());
        fx.expect.barycentreRecentred = Boolean.valueOf(model.classification().wideBinary());
        fx.expect.hasBarycentreMutualRing = Boolean.valueOf(model.hasBarycentreMutualRing());
        return fx;
    }

}
