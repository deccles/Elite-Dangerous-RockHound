package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;

/**
 * Elite journal {@code BodyID:0} arrival-star scans often omit {@code Parents[]}. That is complete data — the
 * star is the system origin at {@code (0,0,0)}, not a missing parent chain.
 */
public final class ArrivalStarRoot {

    private ArrivalStarRoot() {
    }

    public static boolean isJournalArrivalStar(ScanRecord scan) {
        if (scan == null || scan.bodyId() != 0) {
            return false;
        }
        if (!isStellar(scan)) {
            return false;
        }
        return scan.parents() == null || scan.parents().isEmpty();
    }

    public static boolean isSystemOrigin(BodyNode body) {
        return body != null
                && body.bodyId() == 0
                && body.kind() == BodyKind.STAR
                && body.orbitParent() == null;
    }

    private static boolean isStellar(ScanRecord scan) {
        if ("Star".equalsIgnoreCase(scan.bodyType())) {
            return true;
        }
        return scan.bodyId() == 0;
    }
}
