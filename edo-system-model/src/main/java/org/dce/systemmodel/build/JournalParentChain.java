package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.ParentRef;

import java.util.List;

/** Journal {@code Parents[]} is inner-to-outer; {@code [0]} is the immediate orbit parent. */
public final class JournalParentChain {

    private JournalParentChain() {
    }

    public static ParentRef immediateOrbitParent(List<ParentRef> parents) {
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        return parents.getFirst();
    }

    public static boolean directParentIsNull(List<ParentRef> parents, int nullId) {
        ParentRef p = immediateOrbitParent(parents);
        return p != null && p.type() == ParentRef.ParentType.NULL && p.bodyId() == nullId;
    }
}
