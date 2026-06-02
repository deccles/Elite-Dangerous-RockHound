package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.state.BodyInfo;
import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;

/**
 * Adds {@link ScanRecord} / {@link ScanBaryCentreRecord} rows from SQLite {@link CachedBody} when the append-only
 * journal log is missing scans (FSS/EDSM bodies still in cache).
 */
public final class CachedBodyJournalBridge {

    private CachedBodyJournalBridge() {
    }

    public static List<JournalRecord> mergeMissingFromCache(
            String systemName, List<JournalRecord> normalized, CachedSystem cs) {
        if (cs == null || cs.bodies == null || cs.bodies.isEmpty()) {
            return normalized != null ? normalized : List.of();
        }
        Map<String, JournalRecord> byKey = new LinkedHashMap<>();
        if (normalized != null) {
            for (JournalRecord r : normalized) {
                String key = recordKey(r);
                if (key != null) {
                    byKey.put(key, r);
                }
            }
        }
        int added = 0;
        for (CachedBody cb : cs.bodies) {
            if (cb == null || !cb.scanBarycentreRow) {
                continue;
            }
            JournalRecord bary = toBarycentreRecord(cb, systemName);
            if (bary == null) {
                continue;
            }
            String key = "bary:" + ((ScanBaryCentreRecord) bary).bodyId();
            if (!byKey.containsKey(key)) {
                byKey.put(key, bary);
                added++;
            }
        }
        for (CachedBody cb : cs.bodies) {
            if (cb == null || cb.scanBarycentreRow) {
                continue;
            }
            if (!belongsToSystem(cb, systemName)) {
                continue;
            }
            ScanRecord scan = toScanRecord(cb);
            if (scan == null) {
                continue;
            }
            String key = "scan:" + scan.bodyId();
            if (!byKey.containsKey(key)) {
                byKey.put(key, scan);
                added++;
            }
        }
        return List.copyOf(byKey.values());
    }

    /** Adds {@link ScanRecord} / {@link ScanBaryCentreRecord} rows synthesized from live {@link BodyInfo} cache bodies. */
    public static List<JournalRecord> mergeMissingFromBodyInfo(
            String systemName, List<JournalRecord> normalized, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return normalized != null ? normalized : List.of();
        }
        Map<String, JournalRecord> byKey = new LinkedHashMap<>();
        if (normalized != null) {
            for (JournalRecord r : normalized) {
                String key = recordKey(r);
                if (key != null) {
                    byKey.put(key, r);
                }
            }
        }
        for (BodyInfo body : bodies.values()) {
            if (body == null) {
                continue;
            }
            CachedBody cb = cachedBodyFromBodyInfo(body);
            if (body.isScanBarycentreRow()) {
                JournalRecord bary = toBarycentreRecord(cb, systemName);
                if (bary == null) {
                    continue;
                }
                String key = "bary:" + ((ScanBaryCentreRecord) bary).bodyId();
                if (!byKey.containsKey(key)) {
                    byKey.put(key, bary);
                }
                continue;
            }
            ScanRecord scan = toScanRecord(cb);
            if (scan == null) {
                continue;
            }
            String key = "scan:" + scan.bodyId();
            if (!byKey.containsKey(key)) {
                byKey.put(key, scan);
            }
        }
        return List.copyOf(byKey.values());
    }

    private static CachedBody cachedBodyFromBodyInfo(BodyInfo body) {
        CachedBody cb = new CachedBody();
        cb.bodyId = body.getBodyId();
        cb.bodyName = body.getBodyName();
        cb.name = body.getShortName();
        cb.starSystem = body.getStarSystem();
        cb.distanceLs = body.getDistanceLs();
        cb.gravityMS = body.getGravityMS();
        cb.massEm = body.getMassEm();
        cb.planetClass = body.getPlanetClass();
        cb.starType = body.getStarType();
        cb.surfaceTempK = body.getSurfaceTempK();
        cb.orbitalPeriod = body.getOrbitalPeriod();
        cb.semiMajorAxisM = body.getSemiMajorAxisM();
        cb.eccentricity = body.getEccentricity();
        cb.orbitalInclination = body.getOrbitalInclination();
        cb.periapsis = body.getPeriapsis();
        cb.ascendingNode = body.getAscendingNode();
        cb.meanAnomaly = body.getMeanAnomaly();
        cb.orbitalEpochMillis = body.getOrbitalEpochMillis();
        cb.immediateParentBodyId = body.getImmediateParentBodyId();
        cb.journalParentRefs = body.getJournalParentRefs().isEmpty() ? null : body.getJournalParentRefs();
        cb.scanBarycentreRow = body.isScanBarycentreRow();
        cb.wasDiscovered = Boolean.TRUE.equals(body.getWasDiscovered());
        cb.wasMapped = Boolean.TRUE.equals(body.getWasMapped());
        return cb;
    }

    private static boolean belongsToSystem(CachedBody cb, String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return true;
        }
        String name = cb.bodyName != null ? cb.bodyName : cb.name;
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).startsWith(systemName.trim().toLowerCase(Locale.ROOT));
    }

    private static String recordKey(JournalRecord r) {
        if (r instanceof ScanRecord s) {
            return "scan:" + s.bodyId();
        }
        if (r instanceof ScanBaryCentreRecord b) {
            return "bary:" + b.bodyId();
        }
        return null;
    }

    static ScanRecord toScanRecord(CachedBody cb) {
        if (cb.bodyId < 0) {
            return null;
        }
        String name = cb.bodyName != null ? cb.bodyName : cb.name;
        if (name == null || name.isBlank()) {
            return null;
        }
        boolean star = cb.bodyId == 0
                || (cb.starType != null && !cb.starType.isBlank())
                || "Star".equalsIgnoreCase(cb.planetClass);
        String bodyType = star ? "Star" : (isRingCachedBody(cb) ? "Ring" : "Planet");
        String subType = star
                ? (cb.starType != null ? cb.starType : "")
                : (cb.planetClass != null ? cb.planetClass : "");
        Instant epoch = cb.orbitalEpochMillis != null
                ? Instant.ofEpochMilli(cb.orbitalEpochMillis.longValue())
                : Instant.EPOCH;
        return new ScanRecord(
                epoch,
                cb.bodyId,
                name,
                bodyType,
                subType,
                cb.distanceLs,
                cb.massEm != null ? cb.massEm.doubleValue() : 0,
                0,
                cb.gravityMS != null ? cb.gravityMS.doubleValue() : 0,
                cb.surfaceTempK != null ? cb.surfaceTempK.doubleValue() : 0,
                cb.orbitalPeriod != null ? cb.orbitalPeriod.doubleValue() : 0,
                0,
                0,
                0,
                parentsFromCache(cb),
                orbitalElements(cb, epoch),
                Boolean.TRUE.equals(cb.wasDiscovered),
                Boolean.TRUE.equals(cb.wasMapped));
    }

    private static boolean isRingCachedBody(CachedBody cb) {
        if (cb == null) {
            return false;
        }
        String name = cb.bodyName != null ? cb.bodyName : cb.name;
        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.contains("belt cluster") || n.contains("belt ") || n.contains(" ring") || n.endsWith("ring")) {
                return true;
            }
        }
        if (cb.planetClass != null) {
            String pc = cb.planetClass.toLowerCase(Locale.ROOT);
            if (pc.contains("planetary ring") || pc.contains("planetaryring")) {
                return true;
            }
        }
        return false;
    }

    private static ScanBaryCentreRecord toBarycentreRecord(CachedBody cb, String systemName) {
        if (cb.bodyId < 0) {
            return null;
        }
        Instant epoch = cb.orbitalEpochMillis != null
                ? Instant.ofEpochMilli(cb.orbitalEpochMillis.longValue())
                : Instant.EPOCH;
        String name = cb.bodyName != null ? cb.bodyName : cb.name;
        if (name == null || name.isBlank()) {
            name = systemName + " barycentre " + cb.bodyId;
        }
        return new ScanBaryCentreRecord(
                epoch,
                cb.bodyId,
                name,
                parentsFromCache(cb),
                List.of(),
                orbitalElements(cb, epoch));
    }

    private static List<ParentRef> parentsFromCache(CachedBody cb) {
        if (cb.journalParentRefs != null && !cb.journalParentRefs.isEmpty()) {
            List<ParentRef> out = new ArrayList<>();
            for (String ref : cb.journalParentRefs) {
                ParentRef p = parseParentRef(ref);
                if (p != null) {
                    out.add(p);
                }
            }
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        if (cb.immediateParentBodyId >= 0) {
            if (cb.immediateParentBodyId == 0) {
                return List.of(new ParentRef(ParentRef.ParentType.NULL, 0));
            }
            return List.of(new ParentRef(ParentRef.ParentType.NULL, cb.immediateParentBodyId));
        }
        return List.of();
    }

    private static ParentRef parseParentRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        int colon = ref.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            ParentRef.ParentType type = ParentRef.ParentType.fromJournalKey(ref.substring(0, colon));
            int id = Integer.parseInt(ref.substring(colon + 1).trim());
            return new ParentRef(type, id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static OrbitalElements orbitalElements(CachedBody cb, Instant epoch) {
        if (cb.semiMajorAxisM == null || cb.semiMajorAxisM.doubleValue() <= 0) {
            return null;
        }
        return new OrbitalElements(
                cb.semiMajorAxisM.doubleValue(),
                cb.eccentricity != null ? cb.eccentricity.doubleValue() : 0,
                cb.orbitalInclination != null ? Math.toDegrees(cb.orbitalInclination.doubleValue()) : 0,
                cb.periapsis != null ? Math.toDegrees(cb.periapsis.doubleValue()) : 0,
                cb.ascendingNode != null ? Math.toDegrees(cb.ascendingNode.doubleValue()) : 0,
                cb.meanAnomaly != null ? Math.toDegrees(cb.meanAnomaly.doubleValue()) : 0,
                cb.orbitalPeriod != null ? cb.orbitalPeriod.doubleValue() : 0,
                epoch);
    }

    /** True when {@code bodyName} is the main-sequence planet designation (not {@code 5 a} moon). */
    static boolean isPlanetDesignation(String bodyName, String designation) {
        if (bodyName == null || designation == null) {
            return false;
        }
        if (DesignationParser.hasMoonLetterSuffix(bodyName)) {
            return false;
        }
        String shortLabel = DesignationParser.shortLabelFromName(bodyName);
        if (designation.equals(shortLabel)) {
            return true;
        }
        String[] parts = bodyName.trim().split("\\s+");
        if (parts.length >= 2) {
            return designation.equals(parts[parts.length - 1]);
        }
        return false;
    }
}
