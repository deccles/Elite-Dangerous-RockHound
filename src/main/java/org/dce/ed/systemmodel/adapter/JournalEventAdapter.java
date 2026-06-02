package org.dce.ed.systemmodel.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.event.ScanBaryCentreEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;

public final class JournalEventAdapter {

    private JournalEventAdapter() {
    }

    public static ScanRecord fromScanEvent(ScanEvent e) {
        String bodyType;
        if (e.getStarType() != null && !e.getStarType().isBlank()) {
            bodyType = "Star";
        } else if (isRingScanEvent(e)) {
            bodyType = "Ring";
        } else {
            bodyType = "Planet";
        }
        String subType = e.getPlanetClass() != null ? e.getPlanetClass() : e.getStarType();
        return new ScanRecord(
                e.getTimestamp(),
                e.getBodyId(),
                e.getBodyName(),
                bodyType,
                subType != null ? subType : "",
                e.getDistanceFromArrivalLs(),
                e.getMassEm() != null ? e.getMassEm() : 0,
                0,
                e.getSurfaceGravity() != null ? e.getSurfaceGravity() : 0,
                e.getSurfaceTemperature() != null ? e.getSurfaceTemperature() : 0,
                e.getOrbitalPeriod() != null ? e.getOrbitalPeriod() : 0,
                0,
                0,
                0,
                parents(e),
                orbitalElements(e.getSemiMajorAxisM(), e.getEccentricity(), e.getOrbitalInclination(),
                        e.getPeriapsis(), e.getAscendingNode(), e.getMeanAnomaly(), e.getOrbitalPeriod(),
                        e.getTimestamp()),
                Boolean.TRUE.equals(e.getWasDiscovered()),
                Boolean.TRUE.equals(e.getWasMapped()));
    }

    public static ScanBaryCentreRecord fromScanBaryCentreEvent(ScanBaryCentreEvent e) {
        return new ScanBaryCentreRecord(
                e.getTimestamp(),
                e.getBodyId(),
                e.getStarSystem() != null ? e.getStarSystem() + " barycentre " + e.getBodyId() : "barycentre " + e.getBodyId(),
                List.of(),
                List.of(),
                orbitalElements(e.getSemiMajorAxisM(), e.getEccentricity(), e.getOrbitalInclination(),
                        e.getPeriapsis(), e.getAscendingNode(), e.getMeanAnomaly(), e.getOrbitalPeriod(),
                        e.getTimestamp()));
    }

    private static boolean isRingScanEvent(ScanEvent e) {
        if (e == null) {
            return false;
        }
        String name = e.getBodyName();
        if (name != null) {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (n.contains("belt cluster") || n.contains("belt ") || n.contains(" ring") || n.endsWith("ring")) {
                return true;
            }
        }
        String planetClass = e.getPlanetClass();
        if (planetClass != null) {
            String pc = planetClass.toLowerCase(java.util.Locale.ROOT);
            if (pc.contains("planetary ring") || pc.contains("planetaryring")) {
                return true;
            }
        }
        return false;
    }

    private static List<ParentRef> parents(ScanEvent e) {
        List<ParentRef> out = new ArrayList<>();
        if (e.getParents() != null) {
            for (ScanEvent.ParentRef p : e.getParents()) {
                if (p != null) {
                    out.add(new ParentRef(ParentRef.ParentType.fromJournalKey(p.getType()), p.getBodyId()));
                }
            }
        }
        return List.copyOf(out);
    }

    private static OrbitalElements orbitalElements(
            Double sma, Double ecc, Double inc, Double peri, Double node, Double mean, Double period, Instant epoch) {
        if (sma == null || sma <= 0) {
            return null;
        }
        return new OrbitalElements(
                sma,
                ecc != null ? ecc : 0,
                inc != null ? Math.toDegrees(inc) : 0,
                peri != null ? Math.toDegrees(peri) : 0,
                node != null ? Math.toDegrees(node) : 0,
                mean != null ? Math.toDegrees(mean) : 0,
                period != null ? period : 0,
                epoch);
    }
}
