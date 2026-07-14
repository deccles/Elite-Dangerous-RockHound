package org.dce.ed.util;

import java.util.List;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.state.BodyInfo;

/**
 * Central place for first-scan / first-footfall bonus logic.
 * First bonus applies only when we know no one had first footfall and Spansh has no landmarks.
 */
public final class FirstBonusHelper {

    private FirstBonusHelper() {
    }

    /**
     * True if first-discovery bonus applies for payout estimates.
     * No bonus when wasFootfalled is true (someone had first footfall) or when Spansh has landmarks.
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
     * Core logic: first bonus applies only when we know there was no footfall and Spansh was
     * consulted and reported no landmarks.
     * <p>
     * When {@code spanshLandmarks} is {@code null} (not fetched yet), returns {@code false} —
     * omit first-bonus rather than guess. Live UI should {@code getOrFetch} Spansh before calling
     * this so an empty list can mean “resolved, none found” and unlock first-bonus.
     */
    public static boolean firstBonusApplies(Boolean wasFootfalled, List<SpanshLandmark> spanshLandmarks) {
        if (Boolean.TRUE.equals(wasFootfalled)) {
            return false;
        }
        if (spanshLandmarks == null) {
            return false;
        }
        return spanshLandmarks.isEmpty();
    }
}
