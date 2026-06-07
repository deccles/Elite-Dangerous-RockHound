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
        Map<Integer, CachedBody> cacheById = indexCachedBodies(cs.bodies);
        for (CachedBody cb : cs.bodies) {
            if (cb == null || !cb.scanBarycentreRow) {
                continue;
            }
            JournalRecord bary = toBarycentreRecord(cb, systemName, null);
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
            ScanRecord scan = toScanRecord(cb, null, cacheById);
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
        Map<Integer, CachedBody> cacheById = indexCachedBodiesFromBodyInfo(bodies);
        for (BodyInfo body : bodies.values()) {
            if (body == null) {
                continue;
            }
            CachedBody cb = cachedBodyFromBodyInfo(body);
            if (body.isScanBarycentreRow()) {
                JournalRecord bary = toBarycentreRecord(cb, systemName, bodies);
                if (bary == null) {
                    continue;
                }
                String key = "bary:" + ((ScanBaryCentreRecord) bary).bodyId();
                if (!byKey.containsKey(key)) {
                    byKey.put(key, bary);
                }
                continue;
            }
            ScanRecord fromBody = toScanRecord(cb, bodies, cacheById);
            if (fromBody == null) {
                continue;
            }
            String key = "scan:" + fromBody.bodyId();
            JournalRecord existing = byKey.get(key);
            if (existing instanceof ScanRecord incumbent) {
                byKey.put(key, reconcileScanParents(incumbent, fromBody));
            } else {
                byKey.put(key, fromBody);
            }
        }
        return List.copyOf(byKey.values());
    }

    /**
     * Live {@link BodyInfo} rows often carry correct {@code Star:N}/{@code Planet:N} refs while an older journal log
     * entry still has the mistaken {@code Null:N} synthesis from cache-only parent ids.
     */
    static ScanRecord reconcileScanParents(ScanRecord incumbent, ScanRecord fromBodyInfo) {
        if (fromBodyInfo == null) {
            return incumbent;
        }
        if (incumbent == null) {
            return fromBodyInfo;
        }
        if (!shouldRefreshParentsFromBodyInfo(incumbent.parents(), fromBodyInfo.parents())) {
            return incumbent;
        }
        return new ScanRecord(
                incumbent.timestamp(),
                incumbent.bodyId(),
                incumbent.bodyName(),
                incumbent.bodyType(),
                incumbent.subType(),
                incumbent.distanceFromArrivalLs(),
                incumbent.stellarMass(),
                incumbent.radius(),
                incumbent.surfaceGravity(),
                incumbent.surfaceTemperature(),
                incumbent.rotationalPeriod(),
                incumbent.rotationalPeriodTidallyLocked(),
                incumbent.axialTilt(),
                incumbent.terraformState(),
                List.copyOf(fromBodyInfo.parents()),
                incumbent.orbit() != null ? incumbent.orbit() : fromBodyInfo.orbit(),
                incumbent.wasDiscovered(),
                incumbent.wasMapped());
    }

    private static boolean shouldRefreshParentsFromBodyInfo(
            List<ParentRef> journalParents, List<ParentRef> bodyInfoParents) {
        if (bodyInfoParents == null || bodyInfoParents.isEmpty()) {
            return false;
        }
        if (journalParents == null || journalParents.isEmpty()) {
            return true;
        }
        ParentRef journalFirst = journalParents.getFirst();
        ParentRef bodyFirst = bodyInfoParents.getFirst();
        if (journalFirst.type() == ParentRef.ParentType.NULL && journalFirst.bodyId() > 0
                && (bodyFirst.type() == ParentRef.ParentType.STAR
                        || bodyFirst.type() == ParentRef.ParentType.PLANET)) {
            return true;
        }
        if (!bodyInfoParents.equals(journalParents)
                && bodyInfoParents.size() >= journalParents.size()
                && bodyFirst.type() != ParentRef.ParentType.NULL) {
            return journalParents.stream().noneMatch(p -> p.type() == ParentRef.ParentType.STAR
                    || p.type() == ParentRef.ParentType.PLANET);
        }
        return false;
    }

    private static Map<Integer, CachedBody> indexCachedBodiesFromBodyInfo(Map<Integer, BodyInfo> bodies) {
        Map<Integer, CachedBody> out = new LinkedHashMap<>();
        if (bodies == null) {
            return out;
        }
        for (BodyInfo body : bodies.values()) {
            if (body != null && body.getBodyId() >= 0) {
                out.put(Integer.valueOf(body.getBodyId()), cachedBodyFromBodyInfo(body));
            }
        }
        return out;
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
        return toScanRecord(cb, null, null);
    }

    static ScanRecord toScanRecord(CachedBody cb, Map<Integer, BodyInfo> liveBodies, Map<Integer, CachedBody> cacheById) {
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
                parentsFromCache(cb, liveBodies, cacheById),
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
        return toBarycentreRecord(cb, systemName, null);
    }

    private static ScanBaryCentreRecord toBarycentreRecord(
            CachedBody cb, String systemName, Map<Integer, BodyInfo> liveBodies) {
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
                parentsFromCache(cb, liveBodies, null),
                List.of(),
                orbitalElements(cb, epoch));
    }

    private static Map<Integer, CachedBody> indexCachedBodies(List<CachedBody> bodies) {
        Map<Integer, CachedBody> out = new LinkedHashMap<>();
        if (bodies == null) {
            return out;
        }
        for (CachedBody cb : bodies) {
            if (cb != null && cb.bodyId >= 0) {
                out.put(Integer.valueOf(cb.bodyId), cb);
            }
        }
        return out;
    }

    private static List<ParentRef> parentsFromCache(
            CachedBody cb, Map<Integer, BodyInfo> liveBodies, Map<Integer, CachedBody> cacheById) {
        if (cb.journalParentRefs != null && !cb.journalParentRefs.isEmpty()) {
            List<ParentRef> out = new ArrayList<>();
            for (String ref : cb.journalParentRefs) {
                ParentRef p = parseParentRef(ref);
                if (p != null) {
                    out.add(p);
                }
            }
            if (!out.isEmpty()) {
                return correctStellarNullParents(List.copyOf(out), liveBodies, cacheById);
            }
        }
        if (cb.immediateParentBodyId >= 0) {
            if (cb.immediateParentBodyId == 0) {
                return List.of(new ParentRef(ParentRef.ParentType.NULL, 0));
            }
            ParentRef.ParentType type = resolveImmediateParentType(
                    cb.immediateParentBodyId, cb, liveBodies, cacheById);
            return List.of(new ParentRef(type, cb.immediateParentBodyId));
        }
        return List.of();
    }

    private static ParentRef.ParentType resolveImmediateParentType(
            int parentId,
            CachedBody self,
            Map<Integer, BodyInfo> liveBodies,
            Map<Integer, CachedBody> cacheById) {
        BodyInfo live = liveBodies != null ? liveBodies.get(Integer.valueOf(parentId)) : null;
        if (live != null) {
            return parentTypeFromBodyInfo(live);
        }
        CachedBody cached = cacheById != null ? cacheById.get(Integer.valueOf(parentId)) : null;
        if (cached != null) {
            return isCachedStellar(cached) ? ParentRef.ParentType.STAR : ParentRef.ParentType.PLANET;
        }
        if (self != null && DesignationParser.hasMoonLetterSuffix(self.bodyName)) {
            return ParentRef.ParentType.PLANET;
        }
        return ParentRef.ParentType.STAR;
    }

    private static ParentRef.ParentType parentTypeFromBodyInfo(BodyInfo body) {
        if (body.isScanBarycentreRow()) {
            return ParentRef.ParentType.NULL;
        }
        if (body.getBodyId() == 0) {
            return ParentRef.ParentType.STAR;
        }
        String starType = body.getStarType();
        if (starType != null && !starType.isBlank()) {
            return ParentRef.ParentType.STAR;
        }
        String planetClass = body.getPlanetClass();
        if (planetClass != null && "Star".equalsIgnoreCase(planetClass)) {
            return ParentRef.ParentType.STAR;
        }
        return ParentRef.ParentType.PLANET;
    }

    private static boolean isCachedStellar(CachedBody cb) {
        return cb.bodyId == 0
                || (cb.starType != null && !cb.starType.isBlank())
                || "Star".equalsIgnoreCase(cb.planetClass);
    }

    /**
     * SQLite / EDSM rows sometimes store {@code Null:N} where {@code N} is a companion star id; journal scans use
     * {@code Star:N}. Without this correction, parent reconciliation keeps the mistaken hub and hierarchy prunes the body.
     */
    private static List<ParentRef> correctStellarNullParents(
            List<ParentRef> parents, Map<Integer, BodyInfo> liveBodies, Map<Integer, CachedBody> cacheById) {
        if (parents == null || parents.isEmpty()) {
            return parents;
        }
        List<ParentRef> out = new ArrayList<>(parents.size());
        boolean changed = false;
        for (ParentRef p : parents) {
            if (p.type() == ParentRef.ParentType.NULL && p.bodyId() > 0
                    && isStellarBodyId(p.bodyId(), liveBodies, cacheById)) {
                out.add(new ParentRef(ParentRef.ParentType.STAR, p.bodyId()));
                changed = true;
            } else {
                out.add(p);
            }
        }
        return changed ? List.copyOf(out) : parents;
    }

    private static boolean isStellarBodyId(
            int bodyId, Map<Integer, BodyInfo> liveBodies, Map<Integer, CachedBody> cacheById) {
        BodyInfo live = liveBodies != null ? liveBodies.get(Integer.valueOf(bodyId)) : null;
        if (live != null) {
            return parentTypeFromBodyInfo(live) == ParentRef.ParentType.STAR;
        }
        CachedBody cached = cacheById != null ? cacheById.get(Integer.valueOf(bodyId)) : null;
        return cached != null && isCachedStellar(cached);
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
