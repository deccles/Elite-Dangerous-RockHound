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

    /**
     * Locates a system row by name and/or address (first match from the start of the list).
     * <p>
     * When both are provided and they point at different rows (or the address hits a row whose
     * name disagrees), prefers the name match and otherwise treats the identity as not present.
     * That avoids a stale {@code systemAddress} keeping CURRENT locked on an earlier hop after a
     * name-only update.
     * <p>
     * For custom-route loops (duplicate systems), prefer {@link #findSystemRowFrom} with the
     * session's current base index so progress does not snap back to the first occurrence.
     */
    public static int findSystemRow(List<RouteEntry> entries, String systemName, long systemAddress) {
        return findSystemRowFrom(entries, systemName, systemAddress, 0);
    }

    /**
     * Like {@link #findSystemRow} but only considers hops at {@code fromIndexInclusive} and later.
     * Does not wrap to earlier hops — used so looped custom routes advance monotonically.
     *
     * @return row index, or {@code -1} when not found at/after {@code fromIndexInclusive}
     */
    public static int findSystemRowFrom(List<RouteEntry> entries,
            String systemName,
            long systemAddress,
            int fromIndexInclusive) {
        if (entries == null || entries.isEmpty()) {
            return -1;
        }
        int start = Math.max(0, fromIndexInclusive);
        if (start >= entries.size()) {
            return -1;
        }
        int byAddress = -1;
        int byName = -1;
        for (int i = start; i < entries.size(); i++) {
            RouteEntry e = entries.get(i);
            if (e == null || e.isBodyRow) {
                continue;
            }
            if (byAddress < 0 && systemAddress != 0L && e.systemAddress == systemAddress) {
                byAddress = i;
            }
            if (byName < 0 && systemName != null && systemName.equals(e.systemName)) {
                byName = i;
            }
        }
        if (byName >= 0 && byAddress >= 0) {
            return byName;
        }
        if (byName >= 0) {
            return byName;
        }
        if (byAddress >= 0) {
            if (systemName != null && !systemName.isBlank()) {
                RouteEntry matched = entries.get(byAddress);
                if (matched != null && matched.systemName != null && !systemName.equals(matched.systemName)) {
                    return -1;
                }
            }
            return byAddress;
        }
        return -1;
    }

    /** Whether a base/displayed hop matches commander system identity. */
    public static boolean rowMatchesSystem(RouteEntry entry, String systemName, long systemAddress) {
        if (entry == null || entry.isBodyRow) {
            return false;
        }
        if (systemAddress != 0L && entry.systemAddress != 0L && entry.systemAddress == systemAddress) {
            if (systemName == null || systemName.isBlank() || entry.systemName == null
                    || systemName.equals(entry.systemName)) {
                return true;
            }
            return false;
        }
        return systemName != null && !systemName.isBlank() && systemName.equals(entry.systemName);
    }

    /**
     * Last non-body hop of a galaxy-map {@code NavRoute} (the plotted destination).
     *
     * @return {@code null} when there is no system hop
     */
    public static RouteEntry navRouteDestination(List<RouteEntry> navEntries) {
        if (navEntries == null || navEntries.isEmpty()) {
            return null;
        }
        for (int i = navEntries.size() - 1; i >= 0; i--) {
            RouteEntry e = navEntries.get(i);
            if (e != null && !e.isBodyRow) {
                return e;
            }
        }
        return null;
    }

    /**
     * Whether a plotted NavRoute's destination is already a hop on the custom (paste/reorder) route.
     * Intermediate NavRoute hops are ignored — the galaxy map may path through systems that are not
     * on the custom list.
     */
    public static boolean navRouteDestinationOnCustomRoute(List<RouteEntry> navEntries,
            List<RouteEntry> customBase) {
        RouteEntry dest = navRouteDestination(navEntries);
        if (dest == null || customBase == null || customBase.isEmpty()) {
            return false;
        }
        return findSystemRow(customBase, dest.systemName, dest.systemAddress) >= 0;
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
        RouteEntry previousSystem = null;
        for (RouteEntry cur : entries) {
            if (cur == null) {
                continue;
            }
            if (cur.isBodyRow) {
                cur.distanceLy = null;
                continue;
            }
            if (previousSystem == null) {
                cur.distanceLy = null;
            } else if (previousSystem.x == null || previousSystem.y == null || previousSystem.z == null
                    || cur.x == null || cur.y == null || cur.z == null) {
                cur.distanceLy = null;
            } else {
                double dx = cur.x.doubleValue() - previousSystem.x.doubleValue();
                double dy = cur.y.doubleValue() - previousSystem.y.doubleValue();
                double dz = cur.z.doubleValue() - previousSystem.z.doubleValue();
                cur.distanceLy = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            previousSystem = cur;
        }
    }

    public static double cumulativeDistanceLy(List<RouteEntry> entries, int fromRow, int toRow) {
        if (entries == null || fromRow < 0 || toRow >= entries.size() || fromRow >= toRow) {
            return Double.NaN;
        }
        double total = 0.0;
        for (int i = fromRow + 1; i <= toRow; i++) {
            RouteEntry entry = entries.get(i);
            if (entry == null || entry.isBodyRow) {
                continue;
            }
            if (entry.distanceLy == null) {
                return Double.NaN;
            }
            total += entry.distanceLy.doubleValue();
        }
        return total;
    }

    public static void renumberDisplayIndexes(List<RouteEntry> entries) {
        int n = 0;
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

    public static int realSystemCount(List<RouteEntry> entries) {
        if (entries == null) {
            return 0;
        }
        int count = 0;
        for (RouteEntry entry : entries) {
            if (entry != null && !entry.isSynthetic && !entry.isBodyRow) {
                count++;
            }
        }
        return count;
    }
}
