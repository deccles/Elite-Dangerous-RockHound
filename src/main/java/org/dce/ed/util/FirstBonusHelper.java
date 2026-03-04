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
     * Core logic: first bonus applies when wasFootfalled is not true and Spansh does not have landmarks.
     * When spanshLandmarks is null we do not downgrade (treat as unknown).
     */
    public static boolean firstBonusApplies(Boolean wasFootfalled, List<SpanshLandmark> spanshLandmarks) {
        if (Boolean.TRUE.equals(wasFootfalled)) {
            return false;
        }
        boolean spanshHasLandmarks = spanshLandmarks != null && !spanshLandmarks.isEmpty();
        return !spanshHasLandmarks;
    }
}
