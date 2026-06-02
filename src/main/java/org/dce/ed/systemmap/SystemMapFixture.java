package org.dce.ed.systemmap;

import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;

/**
 * JSON fixture describing a known system and expected map rules. See {@code src/test/resources/systemmap/}.
 */
public final class SystemMapFixture {

    public String name;
    public String notes;
    public List<BodySpec> bodies;
    public Expect expect;

    public static final class BodySpec {
        public int id;
        public String bodyName;
        public String shortName;
        public double distanceLs;
        public String starType;
        public String planetClass;
        public String atmoOrType;
        /** Journal {@code Parents[0].BodyID}; use {@code 0} with {@link #parentIsBarycentre} for Null parent. */
        public Integer immediateParentBodyId;
        public Boolean parentIsBarycentre;
        public Double semiMajorAxisM;
        /** Journal orbit elements (same units as live {@code Scan} / cache). */
        public Double eccentricity;
        public Double orbitalInclination;
        public Double periapsis;
        public Double ascendingNode;
        public Double meanAnomaly;
        public Double orbitalPeriod;
        /** Journal {@code ScanBaryCentre} sentinel row for planet-binary {@code Null:N}. */
        public Boolean scanBarycentreRow;
        /** Journal {@code Parents[]} inner-to-outer, e.g. {@code ["Null:14","Star:1","Null:0"]}. */
        public java.util.List<String> journalParents;
    }

    public static final class Expect {
        public String layoutKind;
        public Integer mapStellarCount;
        public List<String> barycentricStarLabels;
        public Boolean barycentreRecentred;
        public Boolean hasBarycentreMutualRing;
        /** Planet–planet binary: shared mutual ring at {@link #planetBinaryNullId}. */
        public Boolean hasPlanetBinaryMutualRing;
        public Integer planetBinaryNullId;
        public Double barycentreMinDistanceFromStarLs;
        public List<String> bodiesOnMutualRing;
        public List<String> bodiesWithoutOwnOrbitRing;
        public List<String> planetsRequiringRings;
        public List<ParentExpect> parents;
        public List<LabelExpect> labelsWhenZoomedOut;
    }

    public static final class ParentExpect {
        public String body;
        public String resolvesTo;
    }

    public static final class LabelExpect {
        public String body;
        public boolean visible;
    }

    public Map<Integer, BodyInfo> toBodies() {
        java.util.HashMap<Integer, BodyInfo> map = new java.util.HashMap<>();
        if (bodies == null) {
            return map;
        }
        for (BodySpec spec : bodies) {
            BodyInfo b = new BodyInfo();
            b.setBodyId(spec.id);
            b.setBodyName(spec.bodyName != null ? spec.bodyName : ("body-" + spec.id));
            if (name != null) {
                b.setStarSystem(name);
            }
            if (spec.shortName != null) {
                b.setBodyShortName(spec.shortName);
            }
            b.setDistanceLs(spec.distanceLs);
            if (spec.starType != null) {
                b.setStarType(spec.starType);
            }
            if (spec.planetClass != null) {
                b.setPlanetClass(spec.planetClass);
            }
            if (spec.atmoOrType != null) {
                b.setAtmoOrType(spec.atmoOrType);
            }
            if (spec.semiMajorAxisM != null) {
                b.setSemiMajorAxisM(spec.semiMajorAxisM);
            }
            if (spec.eccentricity != null) {
                b.setEccentricity(spec.eccentricity);
            }
            if (spec.orbitalInclination != null) {
                b.setOrbitalInclination(spec.orbitalInclination);
            }
            if (spec.periapsis != null) {
                b.setPeriapsis(spec.periapsis);
            }
            if (spec.ascendingNode != null) {
                b.setAscendingNode(spec.ascendingNode);
            }
            if (spec.meanAnomaly != null) {
                b.setMeanAnomaly(spec.meanAnomaly);
            }
            if (spec.orbitalPeriod != null) {
                b.setOrbitalPeriod(spec.orbitalPeriod);
            }
            if (Boolean.TRUE.equals(spec.scanBarycentreRow)) {
                b.setScanBarycentreRow(true);
            }
            if (Boolean.TRUE.equals(spec.parentIsBarycentre)) {
                b.setImmediateParentBodyId(0);
            } else if (spec.immediateParentBodyId != null) {
                b.setImmediateParentBodyId(spec.immediateParentBodyId.intValue());
            } else {
                b.setImmediateParentBodyId(-1);
            }
            if (spec.journalParents != null && !spec.journalParents.isEmpty()) {
                b.setJournalParentRefs(spec.journalParents);
            }
            map.put(Integer.valueOf(spec.id), b);
        }
        return map;
    }

    public int bodyIdByLabel(String label) {
        if (label == null || bodies == null) {
            return -1;
        }
        for (BodySpec spec : bodies) {
            if (label.equals(spec.shortName) || label.equals(spec.bodyName)) {
                return spec.id;
            }
        }
        return -1;
    }
}
