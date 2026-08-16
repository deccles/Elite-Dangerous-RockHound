package org.dce.ed.mission;

/** Actionable reason an optimized Transport plan cannot be produced. */
public record TransportPlanProblem(Code code, long missionId, String message) {
    public enum Code {
        SOURCE_REQUIRED, LOCATION_REQUIRED, CAPACITY_UNKNOWN, COORDINATES_UNAVAILABLE, CARGO_REQUIRED,
        CARGO_SPACE_REQUIRED
    }
}
