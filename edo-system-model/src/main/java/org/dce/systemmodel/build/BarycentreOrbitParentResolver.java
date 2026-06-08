package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;

import java.util.List;
import java.util.Map;

/**
 * {@link org.dce.systemmodel.journal.ScanBaryCentreRecord} rows carry heliocentric elements only — no
 * {@code Parents[]}. When the hub row is empty, the hub's orbit parent is {@code members[0].parents[1]}
 * (first journal ref after the {@code Null:N} hub ref on co-orbit children).
 */
public final class BarycentreOrbitParentResolver {

    private BarycentreOrbitParentResolver() {
    }

    /**
     * @return consensus {@code parents[1]} across members, or {@code null} when absent or members disagree
     */
    public static ParentRef fromMemberChains(
            int baryNullId, List<Integer> memberBodyIds, Map<Integer, ScanRecord> scans) {
        if (memberBodyIds == null || memberBodyIds.isEmpty() || scans == null || scans.isEmpty()) {
            return null;
        }
        ParentRef consensus = null;
        for (int memberId : memberBodyIds) {
            ScanRecord scan = scans.get(memberId);
            if (scan == null) {
                continue;
            }
            ParentRef derived = orbitParentAfterHub(baryNullId, scan.parents());
            if (derived == null) {
                continue;
            }
            if (consensus == null) {
                consensus = derived;
            } else if (!sameRef(consensus, derived)) {
                return null;
            }
        }
        return consensus;
    }

    /** Immediate parent must be {@code Null:baryNullId}; hub orbits {@code parents[1]}. */
    static ParentRef orbitParentAfterHub(int baryNullId, List<ParentRef> parents) {
        if (!JournalParentChain.directParentIsNull(parents, baryNullId) || parents.size() < 2) {
            return null;
        }
        ParentRef outer = parents.get(1);
        if (outer == null || outer.type() == null) {
            return null;
        }
        return outer;
    }

    private static boolean sameRef(ParentRef a, ParentRef b) {
        return a.type() == b.type() && a.bodyId() == b.bodyId();
    }
}
