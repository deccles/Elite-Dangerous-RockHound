package org.dce.systemmodel.journal;

import java.util.List;

/**
 * Resolves journal {@code Null:N} references from {@link ScanRecord#parents()} order.
 * The first parent is the immediate orbit parent; later null entries are ancestral context only.
 */
public final class NullParentRefs {

    private NullParentRefs() {
    }

    /** First parent when it is {@code Null:N} with N &gt; 0 — immediate orbit parent in journal order. */
    public static int innermostNullParentId(List<ParentRef> parents) {
        if (parents == null || parents.isEmpty()) {
            return -1;
        }
        ParentRef first = parents.getFirst();
        if (first.type() == ParentRef.ParentType.NULL && first.bodyId() > 0) {
            return first.bodyId();
        }
        return -1;
    }

    public static int innermostNullParentId(ScanRecord scan) {
        return scan != null ? innermostNullParentId(scan.parents()) : -1;
    }

    public static ParentRef innermostNullParentRef(List<ParentRef> parents) {
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        ParentRef first = parents.getFirst();
        if (first.type() == ParentRef.ParentType.NULL && first.bodyId() > 0) {
            return first;
        }
        return null;
    }

    /** Any {@code Null:N} (N &gt; 0) anywhere in Parents[] — pending ScanBaryCentre / structural refs. */
    public static int anyNullParentIdInChain(ScanRecord scan) {
        if (scan == null || scan.parents() == null) {
            return -1;
        }
        for (ParentRef p : scan.parents()) {
            if (p.type() == ParentRef.ParentType.NULL && p.bodyId() > 0) {
                return p.bodyId();
            }
        }
        return -1;
    }
}
