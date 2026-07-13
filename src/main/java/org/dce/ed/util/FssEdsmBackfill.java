package org.dce.ed.util;

import org.dce.ed.cache.SystemStore;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.state.SystemState;

/**
 * When the game reports FSS completion but the journal has no {@code Scan} events for this visit
 * (e.g. system discovered on another machine or before the overlay tracked it), hydrate the body
 * list from EDSM.
 */
public final class FssEdsmBackfill {

    private static final double FSS_COMPLETE_EPSILON = 1e-9;

    private FssEdsmBackfill() {
    }

    public static boolean isFssComplete(SystemState state) {
        if (state == null) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getAllBodiesFound())) {
            return true;
        }
        Double progress = state.getFssProgress();
        return progress != null && progress.doubleValue() >= 1.0 - FSS_COMPLETE_EPSILON;
    }

    public static boolean needsStandaloneEdsmBackfill(SystemState state) {
        if (!isFssComplete(state)) {
            return false;
        }
        return state.getBodies() == null || state.getBodies().isEmpty();
    }

    public static void backfillIfNeeded(SystemState state, EdsmClient edsmClient, SystemStore cache) {
        if (edsmClient == null || cache == null || !needsStandaloneEdsmBackfill(state)) {
            return;
        }
        String name = state.getSystemName();
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            BodiesResponse edsmBodies = edsmClient.showBodies(name);
            if (edsmBodies != null) {
                cache.mergeBodiesFromEdsm(state, edsmBodies, true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
