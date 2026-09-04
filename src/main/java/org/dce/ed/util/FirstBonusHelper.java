package org.dce.ed.util;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.state.BodyInfo;

/**
 * Vista Genomics first-discovery (5×) bonus for payout estimates.
 * Journal {@code WasMapped} is the only input: bonus applies only when the planet
 * has not been surface-mapped by another commander. Missing/unknown mapping is
 * treated as mapped (no bonus).
 */
public final class FirstBonusHelper {

    private FirstBonusHelper() {
    }

    /**
     * True if first-discovery bonus applies for payout estimates.
     */
    public static boolean firstBonusApplies(BodyInfo body) {
        if (body == null) {
            return false;
        }
        return firstBonusApplies(body.getWasMapped());
    }

    /**
     * True if first-discovery bonus applies for this cached body.
     */
    public static boolean firstBonusApplies(CachedBody body) {
        if (body == null) {
            return false;
        }
        return firstBonusApplies(body.wasMapped);
    }

    /**
     * Bonus applies only when the journal reports the planet as unmapped.
     */
    public static boolean firstBonusApplies(Boolean wasMapped) {
        return Boolean.FALSE.equals(wasMapped);
    }
}
