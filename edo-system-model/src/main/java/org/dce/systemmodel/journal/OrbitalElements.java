package org.dce.systemmodel.journal;

import java.time.Instant;

public record OrbitalElements(
        double semiMajorAxisM,
        double eccentricity,
        double orbitalInclinationDeg,
        double periapsisArgDeg,
        double ascendingNodeDeg,
        double meanAnomalyDeg,
        double orbitalPeriodSec,
        Instant orbitalEpoch) {

    public static OrbitalElements fromRadians(
            double semiMajorAxisM,
            double eccentricity,
            double orbitalInclinationRad,
            double periapsisRad,
            double ascendingNodeRad,
            double meanAnomalyRad,
            double orbitalPeriodSec,
            Instant orbitalEpoch) {
        return new OrbitalElements(
                semiMajorAxisM,
                eccentricity,
                Math.toDegrees(orbitalInclinationRad),
                Math.toDegrees(periapsisRad),
                Math.toDegrees(ascendingNodeRad),
                Math.toDegrees(meanAnomalyRad),
                orbitalPeriodSec,
                orbitalEpoch);
    }
}
