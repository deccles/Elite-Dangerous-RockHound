package org.dce.ed.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Links journal {@code ScanBaryCentre} rows (keyed by {@code BodyID == Null:N}) to their planet hosts.
 * <p>
 * Elite {@code ScanBaryCentre} events carry heliocentric orbital elements only — no {@code Parents[]} and no
 * {@code DistanceFromArrivalLS}. Those fields appear on {@code Scan} events for the moons (e.g. {@code 7 d} lists
 * {@code Null:32}, {@code Planet:28}, {@code Star:0}). This class copies host and distance onto the barycentre row
 * when moon scans arrive (and re-links when a {@code ScanBaryCentre} line is processed after the moons).
 */
public final class ScanBarycentreRows {

    private static final Pattern COMPACT_MOON = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$");

    private ScanBarycentreRows() {
    }

    /**
     * After a moon {@code Scan}, propagate {@code Planet:N} host and outer parent refs onto the existing
     * {@code ScanBaryCentre} row for any {@code Null:N} in {@code Parents}.
     */
    public static void linkPlanetHostedBarycentreFromMoonScan(BodyInfo moon, List<ScanEvent.ParentRef> parents,
            Map<Integer, BodyInfo> bodies) {
        if (moon == null || parents == null || parents.isEmpty() || bodies == null) {
            return;
        }
        if (!isPlanetHostedMoonScan(moon, bodies)) {
            return;
        }
        int nullId = firstNullParentId(parents);
        if (nullId <= 0) {
            nullId = journalNullIdFromRefs(moon);
        }
        if (nullId <= 0) {
            return;
        }
        BodyInfo bary = bodies.get(Integer.valueOf(nullId));
        if (bary == null || !bary.isScanBarycentreRow()) {
            return;
        }
        int planetHostId = firstPlanetParentId(parents, moon, bodies);
        if (planetHostId < 0) {
            return;
        }
        BodyInfo host = bodies.get(Integer.valueOf(planetHostId));
        if (host == null || host.isScanBarycentreRow()) {
            return;
        }
        if (bary.getImmediateParentBodyId() != planetHostId) {
            bary.setImmediateParentBodyId(planetHostId);
        }
        List<String> outerRefs = outerParentRefsExcludingNull(moon.getJournalParentRefs(), nullId);
        if (!outerRefs.isEmpty()) {
            bary.setJournalParentRefs(outerRefs);
        }
        refreshDistanceFromMoonMembers(nullId, bodies);
    }

    /**
     * Live cache often stores {@code Planet:N} without {@code Null:N} on one co-orbit moon. Copy the null ref from a
     * sibling so map layout can parent both to the barycentre hub.
     */
    public static void backfillBinaryMoonNullRefsFromCoOrbitPartners(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return;
        }
        for (BodyInfo bary : bodies.values()) {
            if (bary == null || !bary.isScanBarycentreRow()) {
                continue;
            }
            int nullId = bary.getBodyId();
            if (nullId <= 0 || SystemOrbitGeometry.isCoOrbitMajorSharedNullHub(nullId, bodies)) {
                continue;
            }
            int hostId = bary.getImmediateParentBodyId();
            BodyInfo host = hostId > 0 ? bodies.get(Integer.valueOf(hostId)) : null;
            if (host == null || host.isScanBarycentreRow()) {
                continue;
            }
            String nullRef = "Null:" + nullId;
            for (BodyInfo moon : bodies.values()) {
                if (moon == null || moon.isScanBarycentreRow() || referencesJournalNull(moon, nullId)) {
                    continue;
                }
                if (!isPlanetHostedMoonScan(moon, bodies) && !isCacheParentedHostMoon(moon, hostId, bodies)) {
                    continue;
                }
                String hostDesig = moonParentDesignationFromName(moon);
                if (hostDesig == null) {
                    continue;
                }
                for (BodyInfo other : bodies.values()) {
                    if (other == null || other == moon || other.isScanBarycentreRow()) {
                        continue;
                    }
                    if (!referencesJournalNull(other, nullId)) {
                        continue;
                    }
                    String otherDesig = moonParentDesignationFromName(other);
                    if (otherDesig != null && hostDesig.equalsIgnoreCase(otherDesig)
                            && SystemOrbitGeometry.sharesBinaryMoonDistanceBand(moon, other, nullId, bodies)) {
                        prependJournalNullRef(moon, nullRef);
                        break;
                    }
                }
            }
        }
    }

    private static void prependJournalNullRef(BodyInfo moon, String nullRef) {
        if (moon == null || nullRef == null) {
            return;
        }
        for (String ref : moon.getJournalParentRefs()) {
            if (ref != null && ref.equalsIgnoreCase(nullRef)) {
                return;
            }
        }
        java.util.ArrayList<String> next = new java.util.ArrayList<>();
        next.add(nullRef);
        next.addAll(moon.getJournalParentRefs());
        moon.setJournalParentRefs(next);
    }

    private static boolean isCacheParentedHostMoon(BodyInfo moon, int hostMapKey, Map<Integer, BodyInfo> bodies) {
        if (moon == null || hostMapKey < 0 || bodies == null) {
            return false;
        }
        String name = moon.getShortName();
        if (name == null || name.isBlank()) {
            name = moon.getBodyName();
        }
        if (name == null || !COMPACT_MOON.matcher(name.trim()).matches()) {
            return false;
        }
        return moon.getImmediateParentBodyId() == hostMapKey;
    }

    private static String moonParentDesignationFromName(BodyInfo moon) {
        if (moon == null) {
            return null;
        }
        String name = moon.getShortName();
        if (name == null || name.isBlank()) {
            name = moon.getBodyName();
        }
        if (name == null) {
            return null;
        }
        java.util.regex.Matcher m = COMPACT_MOON.matcher(name.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** After {@code ScanBaryCentre}, link host/distance from moons already in the body map. */
    public static void linkPlanetHostedBarycentreFromMembers(int nullId, Map<Integer, BodyInfo> bodies) {
        if (nullId <= 0 || bodies == null) {
            return;
        }
        BodyInfo bary = bodies.get(Integer.valueOf(nullId));
        if (bary == null || !bary.isScanBarycentreRow()) {
            return;
        }
        if (SystemOrbitGeometry.isCoOrbitMajorSharedNullHub(nullId, bodies)) {
            return;
        }
        int planetHostId = -1;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow() || b.getBodyId() == nullId) {
                continue;
            }
            if (!referencesJournalNull(b, nullId)) {
                continue;
            }
            if (!isPlanetHostedMoonScan(b, bodies)) {
                continue;
            }
            int host = journalPlanetHostFromRefs(b, bodies);
            if (host >= 0) {
                planetHostId = host;
                break;
            }
        }
        if (planetHostId < 0) {
            return;
        }
        if (bary.getImmediateParentBodyId() != planetHostId) {
            bary.setImmediateParentBodyId(planetHostId);
        }
        for (BodyInfo b : bodies.values()) {
            if (b == null || !referencesJournalNull(b, nullId)) {
                continue;
            }
            if (!isPlanetHostedMoonScan(b, bodies)) {
                continue;
            }
            List<String> outerRefs = outerParentRefsExcludingNull(b.getJournalParentRefs(), nullId);
            if (!outerRefs.isEmpty()) {
                bary.setJournalParentRefs(outerRefs);
                break;
            }
        }
        refreshDistanceFromMoonMembers(nullId, bodies);
    }

    private static void refreshDistanceFromMoonMembers(int nullId, Map<Integer, BodyInfo> bodies) {
        BodyInfo bary = bodies.get(Integer.valueOf(nullId));
        if (bary == null) {
            return;
        }
        double sum = 0.0;
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow() || !referencesJournalNull(b, nullId)) {
                continue;
            }
            if (!isPlanetHostedMoonScan(b, bodies)) {
                continue;
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d)) {
                sum += d;
                n++;
            }
        }
        if (n > 0 && Double.isNaN(bary.getDistanceLs())) {
            bary.setDistanceLs(sum / n);
        } else if (n >= 2) {
            double mean = sum / n;
            if (!Double.isFinite(bary.getDistanceLs()) || Math.abs(bary.getDistanceLs() - mean) > 0.5) {
                bary.setDistanceLs(mean);
            }
        }
    }

    private static List<String> outerParentRefsExcludingNull(List<String> childRefs, int nullId) {
        if (childRefs == null || childRefs.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String skip = "Null:" + nullId;
        for (String ref : childRefs) {
            if (ref != null && !ref.equalsIgnoreCase(skip)) {
                out.add(ref);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static int firstNullParentId(List<ScanEvent.ParentRef> parents) {
        if (parents == null) {
            return -1;
        }
        for (ScanEvent.ParentRef p : parents) {
            if (p != null && "Null".equalsIgnoreCase(p.getType()) && p.getBodyId() > 0) {
                return p.getBodyId();
            }
        }
        return -1;
    }

    private static int firstPlanetParentId(List<ScanEvent.ParentRef> parents, BodyInfo moon,
            Map<Integer, BodyInfo> bodies) {
        if (parents == null) {
            return -1;
        }
        for (ScanEvent.ParentRef p : parents) {
            if (p == null || !"Planet".equalsIgnoreCase(p.getType()) || p.getBodyId() < 0) {
                continue;
            }
            if (bodies == null || moon == null || isPlanetHostedMoonScan(moon, bodies)) {
                int mapKey = resolvePlanetMapKey(p.getBodyId(), bodies);
                if (mapKey >= 0) {
                    return mapKey;
                }
            }
        }
        return -1;
    }

    private static int resolvePlanetMapKey(int journalPlanetId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || journalPlanetId < 0) {
            return -1;
        }
        BodyInfo direct = bodies.get(Integer.valueOf(journalPlanetId));
        if (direct != null && !direct.isScanBarycentreRow()) {
            return journalPlanetId;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().getBodyId() == journalPlanetId && !e.getValue().isScanBarycentreRow()) {
                return e.getKey().intValue();
            }
        }
        return journalPlanetId;
    }

    private static int journalNullIdFromRefs(BodyInfo body) {
        if (body == null) {
            return -1;
        }
        for (String ref : body.getJournalParentRefs()) {
            if (ref == null || ref.length() < 6 || !ref.regionMatches(true, 0, "Null:", 0, 5)) {
                continue;
            }
            try {
                return Integer.parseInt(ref.substring(5).trim());
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return -1;
    }

    private static boolean referencesJournalNull(BodyInfo body, int journalNullId) {
        if (body == null || journalNullId <= 0) {
            return false;
        }
        if (body.getImmediateParentBodyId() == journalNullId) {
            return true;
        }
        for (String ref : body.getJournalParentRefs()) {
            if (ref != null && ref.equalsIgnoreCase("Null:" + journalNullId)) {
                return true;
            }
        }
        return false;
    }

    private static int journalPlanetHostFromRefs(BodyInfo child, Map<Integer, BodyInfo> bodies) {
        if (child == null || bodies == null) {
            return -1;
        }
        for (String ref : child.getJournalParentRefs()) {
            if (ref == null || ref.length() < 8 || !ref.regionMatches(true, 0, "Planet:", 0, 7)) {
                continue;
            }
            try {
                int journalId = Integer.parseInt(ref.substring(7).trim());
                return resolvePlanetMapKey(journalId, bodies);
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return -1;
    }

    /** {@code 7 d}-style moon with {@code Null:N} and matching {@code Planet:host} journal refs. */
    private static boolean isPlanetHostedMoonScan(BodyInfo body, Map<Integer, BodyInfo> bodies) {
        if (body == null || body.isScanBarycentreRow() || bodies == null) {
            return false;
        }
        String name = body.getShortName();
        if (name == null || name.isBlank()) {
            name = body.getBodyName();
        }
        if (name == null || !COMPACT_MOON.matcher(name.trim()).matches()) {
            return false;
        }
        return journalNullIdFromRefs(body) > 0 && journalPlanetHostFromRefs(body, bodies) >= 0;
    }
}
