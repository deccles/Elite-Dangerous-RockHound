package org.dce.systemmodel.model;

import org.dce.systemmodel.position.PositionEngine;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SystemModel {

    private final String systemName;
    private final long systemAddress;
    private final Instant referenceEpoch;
    private final Map<Integer, BodyNode> bodies;
    private final Map<Integer, BarycentreNode> barycentres;
    private final HierarchyGraph hierarchy;
    private final PositionEngine positionEngine;

    public SystemModel(
            String systemName,
            long systemAddress,
            Instant referenceEpoch,
            Map<Integer, BodyNode> bodies,
            Map<Integer, BarycentreNode> barycentres,
            HierarchyGraph hierarchy,
            PositionEngine positionEngine) {
        this.systemName = systemName;
        this.systemAddress = systemAddress;
        this.referenceEpoch = referenceEpoch != null ? referenceEpoch : Instant.EPOCH;
        this.bodies = Map.copyOf(bodies);
        this.barycentres = Map.copyOf(barycentres);
        this.hierarchy = hierarchy;
        this.positionEngine = positionEngine;
    }

    public String systemName() {
        return systemName;
    }

    public long systemAddress() {
        return systemAddress;
    }

    public Instant referenceEpoch() {
        return referenceEpoch;
    }

    public Map<Integer, BodyNode> bodies() {
        return bodies;
    }

    public Map<Integer, BarycentreNode> barycentres() {
        return barycentres;
    }

    public HierarchyGraph hierarchy() {
        return hierarchy;
    }

    public Optional<BodyNode> body(int id) {
        return Optional.ofNullable(bodies.get(id));
    }

    public Optional<BarycentreNode> barycentre(int id) {
        return Optional.ofNullable(barycentres.get(id));
    }

    public Position3d positionAt(int bodyId, Instant t) {
        return Position3d.from(positionEngine.positionAt(bodyId, t, true));
    }

    public Map<Integer, Position3d> positionsAt(Instant t) {
        Map<Integer, Position3d> out = new LinkedHashMap<>();
        for (int id : allBodyIds()) {
            out.put(id, positionAt(id, t));
        }
        return Map.copyOf(out);
    }

    public java.util.List<OrbitRing> orbitRingsAt(Instant t) {
        return positionEngine.orbitRingsAt(t);
    }

    public HierarchyGraph definitiveSubgraph() {
        Map<Integer, Integer> parents = new HashMap<>();
        Map<Integer, java.util.List<Integer>> children = new HashMap<>();
        for (var e : hierarchy.parentByChild().entrySet()) {
            int child = e.getKey();
            if (!isDefinitive(child)) {
                continue;
            }
            int parent = e.getValue();
            if (!isDefinitive(parent) && !barycentres.containsKey(parent)) {
                continue;
            }
            parents.put(child, parent);
            children.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(child);
        }
        return new HierarchyGraph(parents, children);
    }

    private java.util.Set<Integer> allBodyIds() {
        java.util.Set<Integer> ids = new java.util.HashSet<>(bodies.keySet());
        ids.addAll(barycentres.keySet());
        return ids;
    }

    private boolean isDefinitive(int bodyId) {
        BodyNode b = bodies.get(bodyId);
        if (b != null) {
            return b.definitive();
        }
        return barycentres.containsKey(bodyId);
    }
}
