package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Replays journal {@code Scan} events for one system and writes a {@link SystemMapFixture} JSON file. Intended for
 * developers refreshing fixtures from a real journal folder.
 * <p>
 * Call {@link #extractFromJournals} / {@link #writeFixture} from a small main or REPL when refreshing fixtures.
 */
public final class SystemMapJournalExtractor {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SystemMapJournalExtractor() {
    }

    /**
     * @return fixture built from the most recent scans of {@code systemName} found in {@code journalDirectory}
     */
    public static SystemMapFixture extractFromJournals(Path journalDirectory, String systemName) throws IOException {
        SystemState state = JournalSystemMapLoader.loadFromJournal(journalDirectory, systemName);
        return toFixture(state);
    }

    public static void writeFixture(SystemMapFixture fixture, Path out) throws IOException {
        Files.writeString(out, GSON.toJson(fixture));
    }

    static SystemMapFixture toFixture(SystemState state) {
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
            double distLs = b.getDistanceLs();
            spec.distanceLs = Double.isFinite(distLs) ? distLs : 0.0;
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
            if (b.isScanBarycentreRow()) {
                spec.scanBarycentreRow = Boolean.TRUE;
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
