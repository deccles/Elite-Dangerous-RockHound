package org.dce.ed.testutil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;

/** Shared assertions for schematic orbit geometry and map polylines. */
public final class OrbitGeometryTestSupport {

    private OrbitGeometryTestSupport() {
    }

    public static int findByShortName(Map<Integer, BodyInfo> bodies, String shortName) {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() != null && shortName.equals(e.getValue().getShortName())) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    public static double distOnAxes(double[] a, double[] b, int axis0, int axis1) {
        double dx = axisCoord(a, axis0) - axisCoord(b, axis0);
        double dy = axisCoord(a, axis1) - axisCoord(b, axis1);
        return Math.hypot(dx, dy);
    }

    public static double axisCoord(double[] p, int axis) {
        return p != null && axis >= 0 && axis < p.length ? p[axis] : 0.0;
    }

    public static OrbitPolylineWorldXY findPlanetBinaryMutualRing(SystemMapModel model, int journalNullId) {
        int ringId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - journalNullId;
        for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == ringId) {
                return p;
            }
        }
        return null;
    }

    /** Body world position should lie on the mutual-orbit circle (constant radius from ring centre). */
    public static void assertBodyOnMutualOrbitRing(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            String shortName, int journalNullId, double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        OrbitPolylineWorldXY ring = findPlanetBinaryMutualRing(model, journalNullId);
        assertNotNull(ring, "mutual orbit ring for Null:" + journalNullId);
        double[] pos = model.positionsMetres().get(Integer.valueOf(mapKey));
        assertNotNull(pos, "position for " + shortName);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double cx = ringCentroid(ring.wx);
        double cy = ringCentroid(ring.wy);
        double ringRad = meanRadius(ring.wx, ring.wy, cx, cy);
        double bodyRad = distOnAxes(pos, new double[] { cx, cy, 0.0 }, a0, a1);
        assertTrue(Math.abs(bodyRad - ringRad) <= toleranceLs * ls,
                shortName + " should sit on mutual ring (bodyR=" + (bodyRad / ls) + " Ls ringR="
                        + (ringRad / ls) + " Ls)");
    }

    public static void assertNoPerBodyOrbitRing(SystemMapModel model, int bodyMapKey) {
        assertFalse(model.hasOrbitRingForBody(bodyMapKey),
                "body id " + bodyMapKey + " should not have its own orbit stroke (uses mutual ring)");
    }

    public static void assertBarycentreFarFromStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int journalNullId, double minDistanceLs) {
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
        int star = SystemOrbitGeometry.schematicCentralStarMapKey(bodies);
        Map<Integer, double[]> pos = model.positionsMetres();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double d = distOnAxes(pos.get(Integer.valueOf(bKey)), pos.get(Integer.valueOf(star)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        assertTrue(d >= minDistanceLs,
                "planet-binary barycentre should not sit on the star; was " + d + " Ls");
    }

    private static double ringCentroid(double[] coords) {
        double sum = 0.0;
        for (double c : coords) {
            sum += c;
        }
        return coords.length > 0 ? sum / coords.length : 0.0;
    }

    private static double meanRadius(double[] wx, double[] wy, double cx, double cy) {
        double sum = 0.0;
        for (int i = 0; i < wx.length; i++) {
            sum += Math.hypot(wx[i] - cx, wy[i] - cy);
        }
        return wx.length > 0 ? sum / wx.length : 0.0;
    }
}
