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

    public Vec3 positionAt(int bodyId, Instant t, boolean strictOrbit) {
        return positionAtRecursive(bodyId, t, strictOrbit, new HashMap<>(), new HashSet<>());
    }

    private Vec3 positionAtRecursive(
            int bodyId, Instant t, boolean strictOrbit, Map<Integer, Vec3> memo, Set<Integer> visiting) {
        if (memo.containsKey(bodyId)) {
            return memo.get(bodyId);
        }
        if (!visiting.add(bodyId)) {
            return Vec3.ZERO;
        }
        try {
            Integer parentId = hierarchy.parentOf(bodyId);
            if (parentId == null || parentId == bodyId) {
                memo.put(bodyId, Vec3.ZERO);
                return Vec3.ZERO;
            }
            Vec3 parentPos = positionAtRecursive(parentId, t, strictOrbit, memo, visiting);
            OrbitalElements orbit = orbitOf(bodyId);
            if (orbit == null) {
                memo.put(bodyId, parentPos);
                return parentPos;
            }
            if (strictOrbit) {
                validateOrbitForEvolution(bodyId, orbit, t);
            }
            double M = KeplerMath.evolvedMeanAnomalyRadians(orbit, t);
            double[] rel = KeplerMath.keplerDisplacementMetres(orbit, M);
            if (rel == null) {
                if (strictOrbit) {
                    throw new MissingOrbitalElementsException(bodyId, List.of("semiMajorAxisM"));
                }
                memo.put(bodyId, parentPos);
                return parentPos;
            }
            Vec3 world = parentPos.plus(new Vec3(rel[0], rel[1], rel[2]));
            memo.put(bodyId, world);
            return world;
        } finally {
            visiting.remove(bodyId);
        }
    }

    public List<OrbitRing> orbitRingsAt(Instant t) {
        List<OrbitRing> rings = new ArrayList<>();
        Map<Integer, Vec3> memo = new HashMap<>();
        Set<Integer> visiting = new HashSet<>();
        for (BodyNode b : bodies.values()) {
            if (b.orbit() == null || b.orbitParent() == null) {
                continue;
            }
            int parentId = b.orbitParent().bodyId();
            rings.add(worldOrbitRing(b.bodyId(), parentId, b.orbit(), t, memo, visiting));
        }
        for (BarycentreNode bc : barycentres.values()) {
            if (bc.orbit() == null || bc.orbitParent() == null) {
                continue;
            }
            rings.add(worldOrbitRing(bc.bodyId(), bc.orbitParent().bodyId(), bc.orbit(), t, memo, visiting));
        }
        return List.copyOf(rings);
    }

    private OrbitRing worldOrbitRing(
            int bodyId, int parentId, OrbitalElements orbit, Instant t, Map<Integer, Vec3> memo,
            Set<Integer> visiting) {
        OrbitRing rel = KeplerOrbitRing.ringForBody(bodyId, parentId, orbit, t);
        if (rel.pointsMetres().isEmpty()) {
            return rel;
        }
        Vec3 parentPos = positionAtRecursive(parentId, t, false, memo, visiting);
        List<double[]> world = new ArrayList<>(rel.pointsMetres().size());
        for (double[] pt : rel.pointsMetres()) {
            world.add(new double[] {
                    parentPos.x() + pt[0],
                    parentPos.y() + pt[1],
                    parentPos.z() + (pt.length >= 3 ? pt[2] : 0.0)
            });
        }
        return new OrbitRing(bodyId, parentId, List.copyOf(world));
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

    private OrbitalElements orbitOf(int bodyId) {
        BodyNode b = bodies.get(bodyId);
        if (b != null) {
            return b.orbit();
        }
        if (HierarchyKeys.isBaryMapKey(bodyId)) {
            BarycentreNode bc = barycentres.get(HierarchyKeys.journalNullFromBaryMapKey(bodyId));
            return bc != null ? bc.orbit() : null;
        }
        BarycentreNode bc = barycentres.get(bodyId);
        return bc != null ? bc.orbit() : null;
    }
}
