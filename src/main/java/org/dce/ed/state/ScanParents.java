package org.dce.ed.state;

import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.logreader.event.ScanEvent;

/**
 * Resolves journal {@code Scan} {@code Parents[]} into the immediate orbit parent id stored on {@link BodyInfo}.
 * Planet-class scans list parents inner-to-outer. Co-orbit planet-binary majors take the first {@code Null:N};
 * moons ({@code A 3 e}) take the first {@code Planet} host even when {@code Null:N} is listed first in the journal.
 */
public final class ScanParents {

    private static final Pattern TRAILING_STAR_BODY_DESIGNATION = Pattern
            .compile("(?<![A-Za-z])([A-Za-z])\\s+(\\d+)(?:\\s+([a-z]+))?\\s*$");

    private ScanParents() {
    }

    /**
     * Elite journal: each {@code Parents} element is typically one keyed ref, inner-to-outer hierarchy.
     */
    public static int immediateOrbitParentBodyId(List<ScanEvent.ParentRef> parents, ScanEvent scan) {
        if (parents == null || parents.isEmpty() || parents.get(0) == null) {
            return -1;
        }
        if (!scanIndicatesStellarBody(scan)) {
            if (scanIndicatesMoonBody(scan)) {
                for (ScanEvent.ParentRef p : parents) {
                    if (p == null || p.getType() == null) {
                        continue;
                    }
                    if ("Planet".equalsIgnoreCase(p.getType())) {
                        return p.getBodyId();
                    }
                }
            }
            for (ScanEvent.ParentRef p : parents) {
                if (p == null || p.getType() == null) {
                    continue;
                }
                if ("Planet".equalsIgnoreCase(p.getType())) {
                    return p.getBodyId();
                }
                if ("Null".equalsIgnoreCase(p.getType()) && p.getBodyId() > 0) {
                    return p.getBodyId();
                }
            }
        }
        return parents.get(0).getBodyId();
    }

    /** Moons ({@code … A 3 e}) vs planet-binary majors ({@code … 1 b}) sharing a {@code Null:N} parent row. */
    public static boolean scanIndicatesMoonBody(ScanEvent scan) {
        if (scan == null || scanIndicatesStellarBody(scan)) {
            return false;
        }
        String name = scan.getBodyName();
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        Matcher trailing = TRAILING_STAR_BODY_DESIGNATION.matcher(trimmed);
        if (!trailing.find()) {
            return false;
        }
        String moon = trailing.group(3);
        return moon != null && !moon.isEmpty();
    }

    /**
     * Star scans carry star type without planet class; planetary scans carry {@link ScanEvent#getPlanetClass()}.
     */
    public static boolean scanIndicatesStellarBody(ScanEvent e) {
        if (e == null) {
            return false;
        }
        String pc = e.getPlanetClass();
        if (pc != null && !pc.trim().isEmpty()) {
            return false;
        }
        String st = e.getStarType();
        return st != null && !st.trim().isEmpty();
    }
}
