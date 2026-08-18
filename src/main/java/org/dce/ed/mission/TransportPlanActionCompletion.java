package org.dce.ed.mission;

/** Persistable evidence that one action at one optimized-plan stop was completed. */
public record TransportPlanActionCompletion(
        int stopIndex, TransportPlanAction.Kind kind, long missionId) {
}
