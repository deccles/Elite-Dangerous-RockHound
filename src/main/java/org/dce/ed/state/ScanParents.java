package org.dce.ed.state;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Resolves journal {@code Scan} {@code Parents[]} into the immediate orbit parent id stored on {@link BodyInfo}.
 * Journal order is inner-to-outer; {@code parents[0]} is the immediate orbit parent (M-0).
 */
public final class ScanParents {

    private ScanParents() {
    }

    public static int immediateOrbitParentBodyId(List<ScanEvent.ParentRef> parents, ScanEvent scan) {
        return immediateOrbitParentBodyId(parents, scan, null);
    }

    public static int immediateOrbitParentBodyId(List<ScanEvent.ParentRef> parents, ScanEvent scan,
            java.util.Map<Integer, BodyInfo> bodies) {
        if (parents == null || parents.isEmpty() || parents.get(0) == null) {
            return -1;
        }
        ScanEvent.ParentRef p = parents.get(0);
        if (p.getType() == null) {
            return -1;
        }
        return switch (p.getType().toLowerCase(java.util.Locale.ROOT)) {
            case "null", "planet", "star" -> p.getBodyId();
            default -> -1;
        };
    }

    /** Moons ({@code … A 3 e}) vs planet-binary majors ({@code … 1 b}) — display classification only. */
    public static boolean scanIndicatesMoonBody(ScanEvent scan) {
        return scanIndicatesMoonBody(scan, null);
    }

    public static boolean scanIndicatesMoonBody(ScanEvent scan, java.util.Map<Integer, BodyInfo> bodies) {
        if (scan == null || scanIndicatesStellarBody(scan)) {
            return false;
        }
        String name = scan.getBodyName();
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        if (SystemOrbitGeometry.hasTrailingStarBodyMoonSuffix(trimmed)) {
            return true;
        }
        return SystemOrbitGeometry.hasEliteMoonDesignationInName(trimmed);
    }

    public static boolean scanIndicatesStellarBody(ScanEvent scan) {
        if (scan == null) {
            return false;
        }
        String starType = scan.getStarType();
        if (starType != null && !starType.isBlank()) {
            return true;
        }
        return scan.getBodyId() == 0;
    }

    private static final Pattern MOON_DESIGNATION = Pattern.compile(".*\\s([A-Za-z])\\s(\\d+)\\s([a-z])\\s*$");

    static boolean moonDesignationMatchesPlanetJournalId(String bodyName, java.util.Map<Integer, BodyInfo> bodies,
            int planetId) {
        if (bodyName == null || bodies == null) {
            return true;
        }
        Matcher m = MOON_DESIGNATION.matcher(bodyName.trim());
        if (!m.matches()) {
            return true;
        }
        BodyInfo host = bodies.get(Integer.valueOf(planetId));
        if (host == null || host.getBodyName() == null) {
            return true;
        }
        String hostName = host.getBodyName().trim();
        String expectedSuffix = " " + m.group(2);
        return hostName.endsWith(expectedSuffix) || hostName.contains(expectedSuffix);
    }
}
