package org.dce.systemmodel.hierarchy;

import org.dce.systemmodel.designation.DesignationParser;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;

import java.util.List;

/**
 * Display metadata for journal {@code ScanBaryCentre} rows on the hierarchy graph. Tree membership follows
 * {@link SystemModel#hierarchy()} only — a barycentre appears when it is a parent in that graph, not via UI filtering.
 */
public final class BarycentreHubPolicy {

    private BarycentreHubPolicy() {
    }

    /** True when the hierarchy graph lists at least one child for this barycentre id. */
    public static boolean hasHierarchyChildren(SystemModel model, int barycentreId) {
        if (model == null || barycentreId <= 0) {
            return false;
        }
        List<Integer> children = model.hierarchy().childrenOf(HierarchyKeys.baryMapKey(barycentreId));
        return children != null && !children.isEmpty();
    }

    /** Tree nodes for barycentres that are parents in {@link SystemModel#hierarchy()}. */
    public static boolean showAsHierarchyHub(SystemModel model, int barycentreId) {
        return hasHierarchyChildren(model, barycentreId);
    }

    /** Planet-hosted binary moon hub ({@code Null:N} with two or more moons on the same planet). */
    public static boolean isPlanetBinaryHub(SystemModel model, int barycentreId) {
        if (!hasHierarchyChildren(model, barycentreId)) {
            return false;
        }
        BarycentreNode bc = model.barycentre(barycentreId).orElse(null);
        if (bc == null || bc.orbitParent() == null
                || bc.orbitParent().type() != ParentRef.ParentType.PLANET) {
            return false;
        }
        int moons = 0;
        for (int childId : model.hierarchy().childrenOf(HierarchyKeys.baryMapKey(barycentreId))) {
            BodyNode child = model.body(childId).orElse(null);
            if (child != null && child.kind() == BodyKind.MOON) {
                moons++;
                if (moons >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Stellar pair (or larger) at a shared {@code Null:N}, not planet-hosted. */
    public static boolean isStellarSubsystemHub(SystemModel model, int barycentreId) {
        if (!hasHierarchyChildren(model, barycentreId)) {
            return false;
        }
        if (isPlanetBinaryHub(model, barycentreId)) {
            return false;
        }
        int nonMoonMembers = 0;
        for (int childId : model.hierarchy().childrenOf(HierarchyKeys.baryMapKey(barycentreId))) {
            BodyNode child = model.body(childId).orElse(null);
            if (child != null && child.kind() != BodyKind.MOON) {
                nonMoonMembers++;
                if (nonMoonMembers >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int planetHostId(BarycentreNode bc) {
        if (bc == null || bc.orbitParent() == null
                || bc.orbitParent().type() != ParentRef.ParentType.PLANET) {
            return -1;
        }
        return bc.orbitParent().bodyId();
    }

    public static String collapsedMoonSummary(SystemModel model, int barycentreId) {
        List<Integer> children = model.hierarchy().childrenOf(HierarchyKeys.baryMapKey(barycentreId));
        if (children == null || children.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int childId : children) {
            BodyNode child = model.body(childId).orElse(null);
            if (child == null || child.kind() != BodyKind.MOON) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(DesignationParser.shortLabelFromName(child.bodyName()));
        }
        return sb.toString();
    }
}
