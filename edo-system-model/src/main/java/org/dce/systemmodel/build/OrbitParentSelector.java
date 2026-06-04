package org.dce.systemmodel.build;

import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.journal.NullParentRefs;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBodyClassification;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.BodyKind;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects orbit parent from journal Parents[] using barycentre knowledge.
 * Binary moons prefer Null barycentre over Planet when a matching ScanBaryCentre exists.
 * Co-orbit majors (two+ bodies sharing the same {@code Null:N}) parent to that barycentre, not the star.
 */
public final class OrbitParentSelector {

    private OrbitParentSelector() {
    }

    public static ParentRef select(
            BodyKind kind,
            ScanRecord self,
            List<ParentRef> parents,
            Set<Integer> knownNullBarycentreIds,
            Map<Integer, List<Integer>> membersByNullId,
            Map<Integer, ScanRecord> scans) {
        if (parents == null || parents.isEmpty()) {
            return new ParentRef(ParentRef.ParentType.STAR, 0);
        }
        Map<Integer, List<Integer>> members =
                membersByNullId != null ? membersByNullId : Map.of();
        Map<Integer, ScanRecord> scanIndex = scans != null ? scans : Map.of();

        if (kind == BodyKind.MOON) {
            ParentRef nullParent = NullParentRefs.innermostNullParentRef(parents);
            if (nullParent != null && knownNullBarycentreIds.contains(nullParent.bodyId())
                    && !isCoOrbitMajorHub(nullParent.bodyId(), members, scanIndex)) {
                return nullParent;
            }
            ParentRef planet = firstOfType(parents, ParentRef.ParentType.PLANET);
            if (planet != null) {
                return planet;
            }
            return parents.getFirst();
        }

        ParentRef innermostNull = NullParentRefs.innermostNullParentRef(parents);
        if (kind == BodyKind.BARYCENTRE && innermostNull != null
                && knownNullBarycentreIds.contains(innermostNull.bodyId())) {
            return innermostNull;
        }
        if (innermostNull != null && knownNullBarycentreIds.contains(innermostNull.bodyId())) {
            int nullId = innermostNull.bodyId();
            if (sharedNullHub(nullId, members, scanIndex) || stellarSharedNullHub(nullId, members, scanIndex)) {
                if (parentsToCoOrbitNullHub(kind, self, nullId, members, scanIndex)) {
                    return innermostNull;
                }
                ParentRef planet = firstOfType(parents, ParentRef.ParentType.PLANET);
                if (planet != null) {
                    return planet;
                }
            }
        }

        if (kind == BodyKind.PLANET || kind == BodyKind.STAR) {
            ParentRef star = firstOfType(parents, ParentRef.ParentType.STAR);
            if (star != null) {
                return star;
            }
            return parents.getFirst();
        }
        if (kind == BodyKind.BARYCENTRE) {
            ParentRef star = firstOfType(parents, ParentRef.ParentType.STAR);
            if (star != null) {
                return star;
            }
            ParentRef planet = firstOfType(parents, ParentRef.ParentType.PLANET);
            if (planet != null) {
                return planet;
            }
            return parents.getFirst();
        }
        ParentRef star = firstOfType(parents, ParentRef.ParentType.STAR);
        return star != null ? star : parents.getFirst();
    }

    /**
     * {@code Null:N} is a hierarchy hub when two or more moons share it (planet-hosted binary) or two or more
     * non-moon members co-orbit (stellar/planetary pair).
     */
    /**
     * One branch star plus a {@link ScanBaryCentreRecord} at the same {@code Null:N} (inner pair in a triple).
     */
    private static boolean stellarSharedNullHub(
            int nullId, Map<Integer, List<Integer>> membersByNullId, Map<Integer, ScanRecord> scans) {
        List<Integer> memberIds = membersByNullId.get(nullId);
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan == null || isMoonScan(scan) || ScanBodyClassification.isRing(scan)) {
                continue;
            }
            if ("Star".equalsIgnoreCase(scan.bodyType())) {
                return true;
            }
        }
        return false;
    }

    static boolean sharedNullHub(
            int nullId, Map<Integer, List<Integer>> membersByNullId, Map<Integer, ScanRecord> scans) {
        List<Integer> memberIds = membersByNullId.get(nullId);
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }
        int moons = 0;
        int nonMoons = 0;
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan == null) {
                continue;
            }
            if (isMoonScan(scan)) {
                moons++;
            } else if (!ScanBodyClassification.isRing(scan)) {
                nonMoons++;
            }
        }
        return moons >= 2 || nonMoons >= 2;
    }

    /** Two or more non-moon majors (planets) share {@code Null:N} — not a planet-hosted moon binary. */
    public static boolean isCoOrbitMajorHub(
            int nullId, Map<Integer, List<Integer>> membersByNullId, Map<Integer, ScanRecord> scans) {
        List<Integer> memberIds = membersByNullId.get(nullId);
        if (memberIds == null || memberIds.size() < 2) {
            return false;
        }
        int nonMoons = 0;
        for (int id : memberIds) {
            ScanRecord scan = scans.get(id);
            if (scan == null || isMoonScan(scan) || ScanBodyClassification.isRing(scan)
                    || "Star".equalsIgnoreCase(scan.bodyType())) {
                continue;
            }
            nonMoons++;
        }
        return nonMoons >= 2;
    }

    /**
     * Only the co-orbit major bodies (e.g. planets 5 and 6) parent to the shared {@code Null:N}; moons, rings, and
     * other satellites use {@code Planet:N} even when journal also lists the co-orbit null.
     */
    private static boolean parentsToCoOrbitNullHub(
            BodyKind kind,
            ScanRecord self,
            int nullId,
            Map<Integer, List<Integer>> membersByNullId,
            Map<Integer, ScanRecord> scans) {
        if (!isCoOrbitMajorHub(nullId, membersByNullId, scans)) {
            return true;
        }
        if (kind == BodyKind.MOON || ScanBodyClassification.isRing(self)) {
            return false;
        }
        return self != null && !isMoonScan(self) && !ScanBodyClassification.isRing(self);
    }

    private static boolean isRingScan(ScanRecord scan) {
        return ScanBodyClassification.isRing(scan);
    }

    private static boolean isMoonScan(ScanRecord scan) {
        if ("Star".equalsIgnoreCase(scan.bodyType())) {
            return false;
        }
        return DesignationParser.hasMoonLetterSuffix(scan.bodyName());
    }

    private static ParentRef firstOfType(List<ParentRef> parents, ParentRef.ParentType type) {
        for (ParentRef p : parents) {
            if (p.type() == type) {
                return p;
            }
        }
        return null;
    }
}
