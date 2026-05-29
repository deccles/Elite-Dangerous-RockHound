package org.dce.ed.state;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.util.SystemOrbitGeometry;

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
        return immediateOrbitParentBodyId(parents, scan, null);
    }

    public static int immediateOrbitParentBodyId(List<ScanEvent.ParentRef> parents, ScanEvent scan,
            Map<Integer, BodyInfo> bodies) {
        if (parents == null || parents.isEmpty() || parents.get(0) == null) {
            return -1;
        }
        if (!scanIndicatesStellarBody(scan)) {
            if (scanIndicatesMoonBody(scan, bodies)) {
                for (ScanEvent.ParentRef p : parents) {
                    if (p == null || p.getType() == null) {
                        continue;
                    }
                    if ("Planet".equalsIgnoreCase(p.getType())) {
                        int planetId = p.getBodyId();
                        if (bodies == null || moonDesignationMatchesPlanetJournalId(scan.getBodyName(), bodies,
                                planetId)) {
                            return planetId;
                        }
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
        return scanIndicatesMoonBody(scan, null);
    }

    public static boolean scanIndicatesMoonBody(ScanEvent scan, Map<Integer, BodyInfo> bodies) {
        if (scan == null || scanIndicatesStellarBody(scan)) {
            return false;
        }
        String name = scan.getBodyName();
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        Matcher trailing = TRAILING_STAR_BODY_DESIGNATION.matcher(trimmed);
        if (trailing.find()) {
            String moon = trailing.group(3);
            if (moon != null && !moon.isEmpty()) {
                return true;
            }
        }
        if (!SystemOrbitGeometry.hasEliteMoonDesignationInName(trimmed)) {
            return false;
        }
        List<ScanEvent.ParentRef> parents = scan.getParents();
        if (parents == null || bodies == null) {
            return false;
        }
        for (ScanEvent.ParentRef p : parents) {
            if (p == null || p.getType() == null) {
                continue;
            }
            if ("Planet".equalsIgnoreCase(p.getType()) && p.getBodyId() > 0
                    && moonDesignationMatchesPlanetJournalId(name, bodies, p.getBodyId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code 7 d} → host designation {@code 7} must match the journal {@code Planet} row's short name, so
     * {@code 1 b} with an unrelated {@code Planet:10} ref stays a planet-binary major.
     */
    private static boolean moonDesignationMatchesPlanetJournalId(String bodyName, Map<Integer, BodyInfo> bodies,
            int planetJournalId) {
        if (bodyName == null || bodies == null || planetJournalId < 0) {
            return false;
        }
        BodyInfo probe = new BodyInfo();
        probe.setBodyName(bodyName);
        String hostDesig = moonHostDesignationFromName(probe);
        if (hostDesig == null) {
            return true;
        }
        BodyInfo planet = bodies.get(Integer.valueOf(planetJournalId));
        if (planet == null) {
            for (BodyInfo row : bodies.values()) {
                if (row != null && row.getBodyId() == planetJournalId) {
                    planet = row;
                    break;
                }
            }
        }
        if (planet == null) {
            return false;
        }
        String label = planet.getShortName();
        if (label == null || label.isBlank()) {
            String full = planet.getBodyName();
            if (full != null) {
                int sp = full.lastIndexOf(' ');
                label = sp >= 0 ? full.substring(sp + 1).trim() : full.trim();
            }
        }
        return label != null && hostDesig.equalsIgnoreCase(label.trim());
    }

    private static String moonHostDesignationFromName(BodyInfo b) {
        String name = b != null ? b.getBodyName() : null;
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        Matcher compact = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$").matcher(trimmed);
        if (compact.matches()) {
            return compact.group(1);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0))) {
                return prev;
            }
        }
        return null;
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
