package org.dce.systemmodel.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.model.Position3d;
import org.junit.jupiter.api.Test;

/** Eol Prou SV-A c15-33: B 7 + B 8 at Null:13 — each member gets its own hub-centred Kepler ring. */
class EolProuSvAC1533RingsTest {

    private static final double LS = 299_792_458.0;

    @Test
    void coOrbitPlanets_eachHaveOwnRingAroundHub() {
        Instant t = Instant.parse("2026-06-08T04:42:37Z");
        int null13 = HierarchyKeys.baryMapKey(13);
        SystemModel model = model(t);

        Optional<OrbitRing> ring7 = findRing(model, t, 14);
        Optional<OrbitRing> ring8 = findRing(model, t, 15);
        Optional<OrbitRing> hubRing = findRing(model, t, null13);

        assertTrue(ring7.isPresent(), "B 7 ring around Null:13");
        assertTrue(ring8.isPresent(), "B 8 ring around Null:13");
        assertTrue(hubRing.isPresent(), "Null:13 heliocentric hub ring");
        assertEquals(null13, ring7.orElseThrow().parentId());
        assertEquals(null13, ring8.orElseThrow().parentId());
        assertEquals(2, hubRing.orElseThrow().parentId());

        double r7 = meanRadius(ring7.orElseThrow(), model, t, 14);
        double r8 = meanRadius(ring8.orElseThrow(), model, t, 15);
        assertTrue(r8 > r7, "B 8 journal SMA larger than B 7");
        assertTrue(Math.abs(r8 - r7) / LS > 0.1, "distinct ring sizes (~0.32 vs ~0.59 Ls)");

        Position3d pos7 = model.positionAt(14, t);
        Position3d pos8 = model.positionAt(15, t);
        assertTrue(onRing(pos7, ring7.orElseThrow()), "B 7 on its ring");
        assertTrue(onRing(pos8, ring8.orElseThrow()), "B 8 on its ring");
        assertFalse(onRing(pos7, ring8.orElseThrow()), "B 7 not on B 8 ring");
    }

    private static SystemModel model(Instant t) {
        OrbitalElements b7Orbit = new OrbitalElements(
                97_264_277.338982, 0.108468, -5.268001, 111.371572, 4822736.918926, -21.886371, 358.171841, t);
        OrbitalElements b8Orbit = new OrbitalElements(
                175_738_751.888275, 0.108468, -5.268001, 291.371567, 4822736.918926, -21.886371, 358.171538, t);
        OrbitalElements hubOrbit = new OrbitalElements(
                33_260_059_356.689453, 0.000452, -0.956466, 186.631531, 6466135.919094, 0.279710, 126.880989, t);
        return new SystemModelBuilder()
                .systemName("Eol Prou SV-A c15-33")
                .add(star(t, 1, List.of(new ParentRef(ParentRef.ParentType.NULL, 0))))
                .add(star(t, 2, List.of(new ParentRef(ParentRef.ParentType.NULL, 0))))
                .add(planet(t, 14, "B 7", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 13),
                        new ParentRef(ParentRef.ParentType.STAR, 2),
                        new ParentRef(ParentRef.ParentType.NULL, 0)), b7Orbit))
                .add(planet(t, 15, "B 8", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 13),
                        new ParentRef(ParentRef.ParentType.STAR, 2),
                        new ParentRef(ParentRef.ParentType.NULL, 0)), b8Orbit))
                .add(new ScanBaryCentreRecord(
                        t, 13, "Eol Prou SV-A c15-33 barycentre 13",
                        List.of(),
                        List.of(),
                        hubOrbit))
                .buildPartial();
    }

    private static Optional<OrbitRing> findRing(SystemModel model, Instant t, int bodyId) {
        return model.orbitRingsAt(t).stream().filter(r -> r.bodyId() == bodyId).findFirst();
    }

    private static double meanRadius(OrbitRing ring, SystemModel model, Instant t, int bodyId) {
        Position3d hub = model.positionAt(model.hierarchy().parentOf(bodyId), t);
        double sum = 0;
        int n = 0;
        for (double[] pt : ring.pointsMetres()) {
            double dx = pt[0] - hub.x();
            double dy = pt[1] - hub.y();
            double dz = (pt.length >= 3 ? pt[2] : 0.0) - hub.z();
            sum += Math.sqrt(dx * dx + dy * dy + dz * dz);
            n++;
        }
        return n > 0 ? sum / n : 0;
    }

    private static boolean onRing(Position3d pos, OrbitRing ring) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] pt : ring.pointsMetres()) {
            double dx = pos.x() - pt[0];
            double dy = pos.y() - pt[1];
            double dz = pos.z() - (pt.length >= 3 ? pt[2] : 0.0);
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best < 5_000.0;
    }

    private static ScanRecord star(Instant t, int id, List<ParentRef> parents) {
        return new ScanRecord(
                t, id, "Star " + id, "Star", "K", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, null, true, false);
    }

    private static ScanRecord planet(
            Instant t, int id, String label, List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Eol Prou SV-A c15-33 " + label, "Planet", "Rocky", 2980,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
