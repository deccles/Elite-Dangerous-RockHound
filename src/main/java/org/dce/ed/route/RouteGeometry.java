package org.dce.ed.route;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D geometry helpers for inserting synthetic systems along the plotted polyline.
 */
public final class RouteGeometry {

    private RouteGeometry() {
    }

    public static List<RouteEntry> deepCopy(List<RouteEntry> entries) {
        List<RouteEntry> out = new ArrayList<>();
        if (entries == null) {
            return out;
        }
        for (RouteEntry e : entries) {
            if (e == null) {
                continue;
            }
            out.add(e.copy());
        }
        return out;
    }

    public static int findSystemRow(List<RouteEntry> entries, String systemName, long systemAddress) {
        if (entries == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            RouteEntry e = entries.get(i);
            if (e == null || e.isBodyRow) {
                continue;
            }
            if (systemAddress != 0L && e.systemAddress == systemAddress) {
                return i;
            }
            if (systemName != null && systemName.equals(e.systemName)) {
                return i;
            }
        }
        return -1;
    }

    public static int bestInsertionIndexByCoords(List<RouteEntry> entries, Double[] coords) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        if (coords == null || coords[0] == null || coords[1] == null || coords[2] == null) {
            return entries.size();
        }
        double[] p = new double[] { coords[0].doubleValue(), coords[1].doubleValue(), coords[2].doubleValue() };
        double best = Double.POSITIVE_INFINITY;
        int bestAfter = entries.size();
        for (int i = 0; i < entries.size() - 1; i++) {
            RouteEntry a = entries.get(i);
            RouteEntry b = entries.get(i + 1);
            if (a == null || b == null || a.isBodyRow || b.isBodyRow) {
                continue;
            }
            if (a.x == null || a.y == null || a.z == null || b.x == null || b.y == null || b.z == null) {
                continue;
            }
            double[] v = new double[] { a.x.doubleValue(), a.y.doubleValue(), a.z.doubleValue() };
            double[] w = new double[] { b.x.doubleValue(), b.y.doubleValue(), b.z.doubleValue() };
            double d = pointToSegmentDistanceSquared(p, v, w);
            if (d < best) {
                best = d;
                bestAfter = i + 1;
            }
        }
        return bestAfter;
    }

    public static double pointToSegmentDistanceSquared(double[] p, double[] v, double[] w) {
        double[] vw = new double[] { w[0] - v[0], w[1] - v[1], w[2] - v[2] };
        double[] vp = new double[] { p[0] - v[0], p[1] - v[1], p[2] - v[2] };
        double c1 = vp[0] * vw[0] + vp[1] * vw[1] + vp[2] * vw[2];
        if (c1 <= 0) {
            return squaredDistance(p, v);
        }
        double c2 = vw[0] * vw[0] + vw[1] * vw[1] + vw[2] * vw[2];
        if (c2 <= c1) {
            return squaredDistance(p, w);
        }
        double t = c1 / c2;
        double[] proj = new double[] { v[0] + t * vw[0], v[1] + t * vw[1], v[2] + t * vw[2] };
        return squaredDistance(p, proj);
    }

    private static double squaredDistance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    public static void recomputeLegDistances(List<RouteEntry> entries) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            RouteEntry cur = entries.get(i);
            if (cur == null || cur.isBodyRow) {
                continue;
            }
            if (i == 0) {
                cur.distanceLy = null;
                continue;
            }
            RouteEntry prev = entries.get(i - 1);
            if (prev == null || prev.isBodyRow) {
                cur.distanceLy = null;
                continue;
            }
            if (prev.x == null || prev.y == null || prev.z == null
                    || cur.x == null || cur.y == null || cur.z == null) {
                cur.distanceLy = null;
                continue;
            }
            double dx = cur.x.doubleValue() - prev.x.doubleValue();
            double dy = cur.y.doubleValue() - prev.y.doubleValue();
            double dz = cur.z.doubleValue() - prev.z.doubleValue();
            cur.distanceLy = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public static void renumberDisplayIndexes(List<RouteEntry> entries) {
        int n = 1;
        if (entries == null) {
            return;
        }
        for (RouteEntry e : entries) {
            if (e == null) {
                continue;
            }
            if (e.isSynthetic || e.isBodyRow) {
                e.displayIndex = null;
                continue;
            }
            e.displayIndex = Integer.valueOf(n);
            n++;
        }
    }
}
