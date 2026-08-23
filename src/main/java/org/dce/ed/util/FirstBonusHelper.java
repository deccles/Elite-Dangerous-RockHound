package org.dce.ed.util;

import java.util.List;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.state.BodyInfo;

/**
 * Central place for first-scan / first-footfall bonus logic.
 * Journal footfall state is authoritative. Spansh landmarks are used only when journal footfall
 * state is unknown; completely unknown state receives the optimistic payout estimate.
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
        return firstBonusApplies(body.getWasFootfalled(), body.getSpanshLandmarks());
    }

    /**
     * True if first-discovery bonus applies for this cached body.
     */
    public static boolean firstBonusApplies(CachedBody body) {
        if (body == null) {
            return false;
        }
        return firstBonusApplies(body.wasFootfalled, body.spanshLandmarks);
    }

    /**
     * Core logic: an explicit journal footfall value wins. When the journal value is unknown,
     * Spansh landmarks suppress the bonus; absent or unresolved Spansh data receives the optimistic
     * estimate.
     * <p>
     * This keeps estimates useful for newly discovered systems that Spansh has not indexed yet.
     */
    public static boolean firstBonusApplies(Boolean wasFootfalled, List<SpanshLandmark> spanshLandmarks) {
        if (wasFootfalled != null) {
            return !wasFootfalled;
        }
        return spanshLandmarks == null || spanshLandmarks.isEmpty();
    }
}
