package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

/**
 * When SQLite cache rows were truncated by partial {@code storeSystem} saves (pre-merge fix) or incremental
 * journal rescans skipped older {@code Scan} lines, replay journal {@code Scan}/{@code ScanBaryCentre} for the
 * same system and union bodies into the working map.
 */
public final class SystemMapJournalEnricher {

    /** Skip journal replay when {@code true} (tests, profiling). */
    public static final String SKIP_PROPERTY = "edo.systemmap.skipJournalEnrich";

    static volatile Path journalDirectoryOverride;

    private SystemMapJournalEnricher() {
    }

    /** Test-only: force journal replay from a directory of {@code Journal.*.log} files. */
    public static void setJournalDirectoryOverrideForTests(Path dir) {
        journalDirectoryOverride = dir;
    }

    public static void clearJournalDirectoryOverrideForTests() {
        journalDirectoryOverride = null;
    }

    static Path resolveJournalDirectory() {
        Path override = journalDirectoryOverride;
        if (override != null) {
            return override;
        }
        return SystemMapSystemLoader.journalDirectory();
    }

    /**
     * @return number of bodies newly added from journal replay
     */
    public static int mergeMissingBodiesFromJournal(Map<Integer, BodyInfo> target, String systemName) {
        if (Boolean.getBoolean(SKIP_PROPERTY)) {
            return 0;
        }
        if (target == null || systemName == null || systemName.isBlank()) {
            return 0;
        }
        Path journalDir = resolveJournalDirectory();
        if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
            return 0;
        }
        try {
            SystemState journal = JournalSystemMapLoader.loadFromJournal(journalDir, systemName);
            return mergeMapBodies(target, journal.getBodies());
        } catch (IOException ignored) {
            return 0;
        }
    }

    /**
     * @return {@code true} if {@code target} gained at least one map-relevant body
     */
    public static boolean enrichMapBodiesFromJournalIfSparse(
            Map<Integer, BodyInfo> target,
            String systemName,
            int cachedOrStateCount) {
        return mergeMissingBodiesFromJournal(target, systemName) > 0;
    }

    /**
     * Before {@link org.dce.ed.cache.SystemCache#storeSystem}, union journal scans when disk cache is sparser
     * than journal history (repairs truncated Coeus-style rows on next overlay persist).
     */
    public static void enrichStateFromJournalIfSparse(SystemState state, CachedSystem existingOnDisk) {
        if (Boolean.getBoolean(SKIP_PROPERTY) || state == null) {
            return;
        }
        String name = state.getSystemName();
        if (name == null || name.isBlank() || state.getBodies().isEmpty()) {
            return;
        }
        // Avoid replaying full journal history on every persist for large, complete cache rows.
        if (existingOnDisk != null && existingOnDisk.bodies != null && existingOnDisk.bodies.size() > 24) {
            Integer total = existingOnDisk.totalBodies;
            if (total == null || existingOnDisk.bodies.size() >= total.intValue()) {
                return;
            }
        }
        int disk = existingOnDisk != null && existingOnDisk.bodies != null
                ? existingOnDisk.bodies.size()
                : 0;
        int mem = state.getBodies().size();
        enrichMapBodiesFromJournalIfSparse(state.getBodies(), name, Math.max(disk, mem));
    }

    static int countMapRelevantBodies(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b != null && isMapRelevantBody(b)) {
                n++;
            }
        }
        return n;
    }

    static boolean isMapRelevantBody(BodyInfo b) {
        if (b == null) {
            return false;
        }
        if (b.getBodyId() < 0) {
            return false;
        }
        return !isDebrisBeltClusterName(b.getBodyName());
    }

    /**
     * Debris belt clusters only — not planetary ring belts ({@code A Ring} / {@code B Ring} scan rows).
     */
    static boolean isDebrisBeltClusterName(String bodyName) {
        if (bodyName == null) {
            return false;
        }
        String n = bodyName.toLowerCase(Locale.ROOT);
        return n.contains("belt cluster") || n.contains("belt ");
    }

    /**
     * Union {@code source} into {@code target}; journal fields win when the keeper lacks orbit/parent data.
     *
     * @return number of bodies newly added to {@code target}
     */
    static int mergeMapBodies(Map<Integer, BodyInfo> target, Map<Integer, BodyInfo> source) {
        if (target == null || source == null || source.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (Map.Entry<Integer, BodyInfo> e : source.entrySet()) {
            BodyInfo src = e.getValue();
            if (src == null || !isMapRelevantBody(src)) {
                continue;
            }
            Integer key = e.getKey();
            BodyInfo keep = target.get(key);
            if (keep == null) {
                BodyInfo copy = copyBodyForMerge(src);
                copy.setBodyId(key.intValue());
                target.put(key, copy);
                added++;
                continue;
            }
            mergeBodyFields(keep, src);
        }
        return added;
    }

    private static BodyInfo copyBodyForMerge(BodyInfo src) {
        BodyInfo copy = new BodyInfo();
        mergeBodyFields(copy, src);
        return copy;
    }

    private static void mergeBodyFields(BodyInfo keep, BodyInfo drop) {
        if (keep == null || drop == null) {
            return;
        }
        if (keep.getStarSystem() == null && drop.getStarSystem() != null) {
            keep.setStarSystem(drop.getStarSystem());
        }
        if (keep.getBodyName() == null && drop.getBodyName() != null) {
            keep.setBodyName(drop.getBodyName());
        }
        if (keep.getShortName() == null && drop.getShortName() != null) {
            keep.setBodyShortName(drop.getShortName());
        }
        if (Double.isNaN(keep.getDistanceLs()) && !Double.isNaN(drop.getDistanceLs())) {
            keep.setDistanceLs(drop.getDistanceLs());
        }
        if (keep.getPlanetClass() == null && drop.getPlanetClass() != null) {
            keep.setPlanetClass(drop.getPlanetClass());
        }
        if (keep.getStarType() == null && drop.getStarType() != null) {
            keep.setStarType(drop.getStarType());
        }
        if (keep.getImmediateParentBodyId() < 0 && drop.getImmediateParentBodyId() >= 0) {
            keep.setImmediateParentBodyId(drop.getImmediateParentBodyId());
        }
        if (keep.getJournalParentRefs().isEmpty() && !drop.getJournalParentRefs().isEmpty()) {
            keep.setJournalParentRefs(drop.getJournalParentRefs());
        }
        if (!drop.isScanBarycentreRow()) {
            // keep scan bary flag if already set
        } else if (!keep.isScanBarycentreRow()) {
            keep.setScanBarycentreRow(true);
        }
        if (keep.getSemiMajorAxisM() == null && drop.getSemiMajorAxisM() != null) {
            keep.setSemiMajorAxisM(drop.getSemiMajorAxisM());
        }
        if (keep.getOrbitalPeriod() == null && drop.getOrbitalPeriod() != null) {
            keep.setOrbitalPeriod(drop.getOrbitalPeriod());
        }
        if (keep.getEccentricity() == null && drop.getEccentricity() != null) {
            keep.setEccentricity(drop.getEccentricity());
        }
        if (keep.getOrbitalInclination() == null && drop.getOrbitalInclination() != null) {
            keep.setOrbitalInclination(drop.getOrbitalInclination());
        }
        if (keep.getPeriapsis() == null && drop.getPeriapsis() != null) {
            keep.setPeriapsis(drop.getPeriapsis());
        }
        if (keep.getAscendingNode() == null && drop.getAscendingNode() != null) {
            keep.setAscendingNode(drop.getAscendingNode());
        }
        if (keep.getMeanAnomaly() == null && drop.getMeanAnomaly() != null) {
            keep.setMeanAnomaly(drop.getMeanAnomaly());
        }
        if (keep.getOrbitalEpochMillis() == null && drop.getOrbitalEpochMillis() != null) {
            keep.setOrbitalEpochMillis(drop.getOrbitalEpochMillis());
        }
    }

    static Map<Integer, BodyInfo> mergeCopy(Map<Integer, BodyInfo> base, Map<Integer, BodyInfo> extra) {
        Map<Integer, BodyInfo> merged = new HashMap<>();
        if (base != null) {
            for (Map.Entry<Integer, BodyInfo> e : base.entrySet()) {
                if (e.getValue() != null) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
        }
        mergeMapBodies(merged, extra);
        return merged;
    }
}
