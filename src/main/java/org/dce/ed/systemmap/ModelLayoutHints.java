package org.dce.ed.systemmap;

import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.journal.ParentRef;

/**
 * Layout classification derived from {@link SystemModel} structure (not cache-only heuristics).
 */
public final class ModelLayoutHints {

    public final boolean hierarchicalWide;
    public final boolean hierarchicalTriple;
    public final boolean cohesiveCompanionPair;
    public final int primaryStarBodyId;
    public final int innerStellarNullId;

    ModelLayoutHints(
            boolean hierarchicalWide,
            boolean hierarchicalTriple,
            boolean cohesiveCompanionPair,
            int primaryStarBodyId,
            int innerStellarNullId) {
        this.hierarchicalWide = hierarchicalWide;
        this.hierarchicalTriple = hierarchicalTriple;
        this.cohesiveCompanionPair = cohesiveCompanionPair;
        this.primaryStarBodyId = primaryStarBodyId;
        this.innerStellarNullId = innerStellarNullId;
    }

    public static ModelLayoutHints from(SystemModel model, Map<Integer, BodyInfo> bodies) {
        if (model == null || bodies == null || bodies.isEmpty()) {
            return new ModelLayoutHints(false, false, true, -1, -1);
        }
        int primaryId = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            for (BodyNode b : model.bodies().values()) {
                if (b.kind() == BodyKind.STAR && b.orbitParent() != null
                        && b.orbitParent().type() == ParentRef.ParentType.NULL
                        && b.orbitParent().bodyId() == 0) {
                    primaryId = b.bodyId();
                    break;
                }
            }
        }
        HierarchyGraph hg = model.hierarchy();
        int innerNull = -1;
        int branchStars = 0;
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() != BodyKind.STAR || b.bodyId() == primaryId) {
                continue;
            }
            branchStars++;
            Integer p = hg.parentOf(b.bodyId());
            if (p != null && HierarchyKeys.isBaryMapKey(p.intValue())) {
                int nullId = HierarchyKeys.journalNullFromBaryMapKey(p.intValue());
                if (innerNull < 0) {
                    innerNull = nullId;
                }
            }
        }
        boolean hierarchical = branchStars >= 1 && hasStellarBaryUnderPrimary(model, hg, primaryId);
        boolean triple = innerNull > 0
                && SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies);
        boolean cohesive = SystemOrbitGeometry.hierarchicalCompanionBranchStarsCohesive(bodies);
        return new ModelLayoutHints(hierarchical, triple, cohesive, primaryId, innerNull);
    }

    private static boolean hasStellarBaryUnderPrimary(SystemModel model, HierarchyGraph hg, int primaryId) {
        if (primaryId < 0) {
            return false;
        }
        for (BarycentreNode bc : model.barycentres().values()) {
            if (bc.orbitParent() != null
                    && bc.orbitParent().type() == ParentRef.ParentType.STAR
                    && bc.orbitParent().bodyId() == primaryId) {
                return true;
            }
            Integer p = hg.parentOf(HierarchyKeys.baryMapKey(bc.bodyId()));
            if (p != null && p.intValue() == primaryId) {
                return true;
            }
        }
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() != BodyKind.STAR || b.bodyId() == primaryId) {
                continue;
            }
            Integer p = hg.parentOf(b.bodyId());
            if (p != null && p.intValue() == primaryId) {
                return true;
            }
            if (p != null && HierarchyKeys.isBaryMapKey(p.intValue())) {
                return true;
            }
        }
        return branchStarCount(model, primaryId) >= 1;
    }

    private static int branchStarCount(SystemModel model, int primaryId) {
        int n = 0;
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() == BodyKind.STAR && b.bodyId() != primaryId) {
                n++;
            }
        }
        return n;
    }
}
