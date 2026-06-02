package org.dce.systemmodel.journal;

import java.time.Instant;
import java.util.List;

public record ScanRecord(
        Instant timestamp,
        int bodyId,
        String bodyName,
        String bodyType,
        String subType,
        double distanceFromArrivalLs,
        double stellarMass,
        double radius,
        double surfaceGravity,
        double surfaceTemperature,
        double rotationalPeriod,
        double rotationalPeriodTidallyLocked,
        double axialTilt,
        double terraformState,
        List<ParentRef> parents,
        OrbitalElements orbit,
        boolean wasDiscovered,
        boolean wasMapped) implements JournalRecord {
}
