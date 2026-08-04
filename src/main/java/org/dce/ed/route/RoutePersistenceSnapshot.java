package org.dce.ed.route;

/**
 * Serializable subset of {@link RouteSession} for overlay session persistence.
 * Mapping to {@code EdoSessionState} lives in {@code org.dce.ed} (adapter).
 */
public record RoutePersistenceSnapshot(
        String currentSystemName,
        Long currentSystemAddress,
        double[] currentStarPos,
        String targetSystemName,
        Long targetSystemAddress,
        Long destinationSystemAddress,
        Integer destinationBodyId,
        String destinationName,
        String pendingJumpLockedName,
        Long pendingJumpLockedAddress,
        Boolean inHyperspace,
        /** Monotonic CURRENT hop index into the base route (custom-route loops). */
        Integer currentBaseIndex) {
}
