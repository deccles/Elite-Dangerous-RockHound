package org.dce.ed;

import org.dce.ed.route.RoutePersistenceSnapshot;
import org.dce.ed.session.EdoSessionState;

/**
 * Maps route navigation fields between {@link EdoSessionState} and {@link org.dce.ed.route.RoutePersistenceSnapshot}.
 */
public final class RoutePersistenceAdapter {

    private RoutePersistenceAdapter() {
    }

    public static RoutePersistenceSnapshot fromEdoSession(EdoSessionState state) {
        if (state == null) {
            return null;
        }
        return new RoutePersistenceSnapshot(
                state.getCurrentSystemName(),
                state.getCurrentSystemAddress(),
                state.getCurrentStarPos(),
                state.getTargetSystemName(),
                state.getTargetSystemAddress(),
                state.getDestinationSystemAddress(),
                state.getDestinationBodyId(),
                state.getDestinationName(),
                state.getPendingJumpLockedName(),
                state.getPendingJumpLockedAddress(),
                state.getInHyperspace());
    }

    public static void fillEdoSession(EdoSessionState state, RoutePersistenceSnapshot snap) {
        if (state == null || snap == null) {
            return;
        }
        state.setCurrentSystemName(snap.currentSystemName());
        state.setCurrentSystemAddress(snap.currentSystemAddress());
        state.setCurrentStarPos(snap.currentStarPos());
        state.setTargetSystemName(snap.targetSystemName());
        state.setTargetSystemAddress(snap.targetSystemAddress());
        state.setDestinationSystemAddress(snap.destinationSystemAddress());
        state.setDestinationBodyId(snap.destinationBodyId());
        state.setDestinationName(snap.destinationName());
        state.setPendingJumpLockedName(snap.pendingJumpLockedName());
        state.setPendingJumpLockedAddress(snap.pendingJumpLockedAddress());
        state.setInHyperspace(snap.inHyperspace());
    }

}
