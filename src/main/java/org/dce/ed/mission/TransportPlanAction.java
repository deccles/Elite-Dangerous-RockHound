package org.dce.ed.mission;

/** Cargo change performed during one optimized station visit. */
public record TransportPlanAction(Kind kind, long missionId, String commodity, int tons) {
    public enum Kind { PICK_UP, DELIVER, VISIT }
}
