package org.dce.systemmodel.position;

import org.dce.systemmodel.exception.MissingOrbitalElementsException;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.Vec3;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PositionEngine {

    private final Map<Integer, BodyNode> bodies;
    private final Map<Integer, BarycentreNode> barycentres;
    private final HierarchyGraph hierarchy;

    public PositionEngine(
            Map<Integer, BodyNode> bodies,
            Map<Integer, BarycentreNode> barycentres,
            HierarchyGraph hierarchy) {
        this.bodies = bodies;
        this.barycentres = barycentres;
        this.hierarchy = hierarchy;
    }

    public Vec3 positionAt(int mapKey, Instant t, boolean strictOrbit) {
        return positionAtRecursive(mapKey, t, strictOrbit, new HashMap<>(), new HashSet<>());
    }

    private Vec3 positionAtRecursive(
            int mapKey, Instant t, boolean strictOrbit, Map<Integer, Vec3> memo, Set<Integer> visiting) {
        if (memo.containsKey(mapKey)) {
            return memo.get(mapKey);
        }
        if (!visiting.add(mapKey)) {
            return Vec3.ZERO;
        }
        try {
            Integer parentKey = hierarchy.parentOf(mapKey);
            if (parentKey == null || parentKey == mapKey) {
                memo.put(mapKey, Vec3.ZERO);
                return Vec3.ZERO;
            }
            Vec3 parentPos = positionAtRecursive(parentKey, t, strictOrbit, memo, visiting);

            OrbitalElements orbit = orbitOf(mapKey);
            if (orbit != null) {
                if (strictOrbit) {
                    validateOrbitForEvolution(mapKey, orbit, t);
                }
                double M = KeplerMath.evolvedMeanAnomalyRadians(orbit, t);
                double[] rel = KeplerMath.keplerDisplacementMetres(orbit, M);
                if (rel != null) {
                    Vec3 world = parentPos.plus(new Vec3(rel[0], rel[1], rel[2]));
                    memo.put(mapKey, world);
                    return world;
                }
                if (strictOrbit) {
                    throw new MissingOrbitalElementsException(mapKey, List.of("semiMajorAxisM"));
                }
            }

            BarycentreNode bc = barycentreForMapKey(mapKey);
            if (bc != null && bc.childBodyIds() != null && !bc.childBodyIds().isEmpty()) {
                Vec3 sum = Vec3.ZERO;
                int count = 0;
                for (int memberId : bc.childBodyIds()) {
                    BodyNode member = bodies.get(memberId);
                    if (member == null || !member.definitive()) {
                        continue;
                    }
                    Vec3 memberPos = positionAtRecursive(memberId, t, strictOrbit, memo, visiting);
                    sum = sum.plus(memberPos);
                    count++;
                }
                if (count > 0) {
                    Vec3 centroid = new Vec3(sum.x() / count, sum.y() / count, sum.z() / count);
                    memo.put(mapKey, centroid);
                    return centroid;
                }
            }

            memo.put(mapKey, parentPos);
            return parentPos;
        } finally {
            visiting.remove(mapKey);
        }
    }

    public List<OrbitRing> orbitRingsAt(Instant t) {
        List<OrbitRing> rings = new ArrayList<>();
        Map<Integer, Vec3> memo = new HashMap<>();
        Set<Integer> visiting = new HashSet<>();

        for (BarycentreNode bc : barycentres.values()) {
            int hubKey = HierarchyKeys.baryMapKey(bc.bodyId());
            Integer parentKey = hierarchy.parentOf(hubKey);
            if (bc.orbit() != null && parentKey != null) {
                rings.add(worldOrbitRing(hubKey, parentKey, bc.orbit(), t, memo, visiting));
            }
        }
        for (BodyNode b : bodies.values()) {
            if (!b.definitive() || b.orbit() == null) {
                continue;
            }
            Integer parentKey = hierarchy.parentOf(b.bodyId());
            if (parentKey == null) {
                continue;
            }
            rings.add(worldOrbitRing(b.bodyId(), parentKey, b.orbit(), t, memo, visiting));
        }
        return List.copyOf(rings);
    }

    private OrbitRing worldOrbitRing(
            int bodyId, int parentKey, OrbitalElements orbit, Instant t, Map<Integer, Vec3> memo,
            Set<Integer> visiting) {
        OrbitRing rel = KeplerOrbitRing.ringForBody(bodyId, parentKey, orbit, t);
        if (rel.pointsMetres().isEmpty()) {
            return rel;
        }
        Vec3 parentPos = positionAtRecursive(parentKey, t, false, memo, visiting);
        List<double[]> world = new ArrayList<>(rel.pointsMetres().size());
        for (double[] pt : rel.pointsMetres()) {
            world.add(new double[] {
                    parentPos.x() + pt[0],
                    parentPos.y() + pt[1],
                    parentPos.z() + (pt.length >= 3 ? pt[2] : 0.0)
            });
        }
        return new OrbitRing(bodyId, parentKey, List.copyOf(world));
    }

    private static void validateOrbitForEvolution(int bodyId, OrbitalElements orbit, Instant t) {
        List<String> missing = new ArrayList<>();
        if (orbit.semiMajorAxisM() <= 0 || Double.isNaN(orbit.semiMajorAxisM())) {
            missing.add("semiMajorAxisM");
        }
        if (t != null && !t.equals(orbit.orbitalEpoch())) {
            if (orbit.orbitalPeriodSec() <= 1e-6 || Double.isNaN(orbit.orbitalPeriodSec())) {
                missing.add("orbitalPeriodSec");
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingOrbitalElementsException(bodyId, missing);
        }
    }

    private BarycentreNode barycentreForMapKey(int mapKey) {
        if (HierarchyKeys.isBaryMapKey(mapKey)) {
            return barycentres.get(HierarchyKeys.journalNullFromBaryMapKey(mapKey));
        }
        return barycentres.get(mapKey);
    }

    private OrbitalElements orbitOf(int mapKey) {
        BodyNode b = bodies.get(mapKey);
        if (b != null) {
            return b.orbit();
        }
        BarycentreNode bc = barycentreForMapKey(mapKey);
        return bc != null ? bc.orbit() : null;
    }
}
