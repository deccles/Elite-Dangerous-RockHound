package org.dce.ed.testutil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
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
        double dx = SystemOrbitGeometry.worldAxisMetres(a, axis0) - SystemOrbitGeometry.worldAxisMetres(b, axis0);
        double dy = SystemOrbitGeometry.worldAxisMetres(a, axis1) - SystemOrbitGeometry.worldAxisMetres(b, axis1);
        return Math.hypot(dx, dy);
    }

    /** Map-plane coordinates for axis {@code axis} (same frame as orbit polyline {@code wx}/{@code wy}). */
    public static double axisCoord(double[] p, int axis) {
        return SystemOrbitGeometry.worldAxisMetres(p, axis);
    }

    private static double[] mapPlanePoint(double x, double y, int axis0, int axis1) {
        int need = Math.max(3, Math.max(axis0, axis1) + 1);
        double[] out = new double[need];
        out[axis0] = x;
        out[axis1] = y;
        return out;
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
        double bodyRad = distOnAxes(pos, mapPlanePoint(cx, cy, a0, a1), a0, a1);
        double tolM = toleranceLs * ls;
        /* Polyline mean radius can differ slightly from schematic dot radius (segment count, phase at epoch). */
        assertTrue(Math.abs(bodyRad - ringRad) <= Math.max(tolM, ringRad * 0.12),
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

    public static double ringCentroid(double[] coords) {
        double sum = 0.0;
        for (double c : coords) {
            sum += c;
        }
        return coords.length > 0 ? sum / coords.length : 0.0;
    }

    public static double meanRadius(double[] wx, double[] wy, double cx, double cy) {
        double sum = 0.0;
        for (int i = 0; i < wx.length; i++) {
            sum += Math.hypot(wx[i] - cx, wy[i] - cy);
        }
        return wx.length > 0 ? sum / wx.length : 0.0;
    }

    /**
     * Mirrors the screenshot failure: one light-blue circle centred on the arrival star at heliocentric (~50k Ls)
     * scale with B/C on its rim — not the schematic ~7k Ls BCD trunk.
     */
    public static void assertNoHeliocentricRingAroundPrimaryStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId, double maxRingRadiusLs) {
        assertNoHeliocentricRingAroundPrimaryStar(model, bodies, primaryStarId, maxRingRadiusLs,
                model.orbitPolylines());
    }

    public static void assertNoHeliocentricRingAroundPrimaryStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId, double maxRingRadiusLs, List<OrbitPolylineWorldXY> polylines) {
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly != null && poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                fail("system barycentre mutual ring");
            }
        }
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double[] aPos = model.positionsMetres().get(Integer.valueOf(primaryStarId));
        assertNotNull(aPos, "primary position");
        double ax = axisCoord(aPos, a0);
        double ay = axisCoord(aPos, a1);
        double maxRadM = maxRingRadiusLs * ls;
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
                continue;
            }
            if (poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                fail("heliocentric mutual barycentre ring (bodyId=" + poly.bodyId + ")");
            }
            double cx = ringCentroid(poly.wx);
            double cy = ringCentroid(poly.wy);
            double ringRad = meanRadius(poly.wx, poly.wy, cx, cy);
            double centreOff = Math.hypot(cx - ax, cy - ay);
            if (ringRad <= maxRadM || centreOff > ringRad * 0.35) {
                continue;
            }
            int companionOnRim = 0;
            for (String label : new String[] { "B", "C" }) {
                int bid = findByShortName(bodies, label);
                if (bid < 0) {
                    continue;
                }
                double[] p = model.positionsMetres().get(Integer.valueOf(bid));
                if (p == null) {
                    continue;
                }
                double bx = axisCoord(p, a0);
                double by = axisCoord(p, a1);
                double bodyRad = Math.hypot(bx - cx, by - cy);
                if (Math.abs(bodyRad - ringRad) <= ringRad * 0.08) {
                    companionOnRim++;
                }
            }
            if (companionOnRim >= 2) {
                fail("heliocentric-scale ring centred near primary (ringR=" + (ringRad / ls) + " Ls, bodyId="
                        + poly.bodyId + ", centreOff=" + (centreOff / ls) + " Ls)");
            }
        }
    }
}
