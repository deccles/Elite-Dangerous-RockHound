package org.dce.ed.state;

import java.util.List;

import org.dce.ed.logreader.event.ScanEvent;

/**
 * Resolves journal {@code Scan} {@code Parents[]} into the immediate orbit parent id stored on {@link BodyInfo}.
 * Planet-class scans list parents inner-to-outer. Walk that order and take the first {@code Planet} (moon host) or
 * {@code Null:N} (planet-binary barycentre); do not scan all {@code Null}s before {@code Planet}s or moons in deep
 * hierarchies parent to the wrong barycentre.
 */
public final class ScanParents {

    private ScanParents() {
    }

    /**
     * Elite journal: each {@code Parents} element is typically one keyed ref, inner-to-outer hierarchy. Co-orbiting
     * planets list {@code Null:N} before the host {@code Planet}; moons list only {@code Planet} (and {@code Star}).
     */
    public static int immediateOrbitParentBodyId(List<ScanEvent.ParentRef> parents, ScanEvent scan) {
        if (parents == null || parents.isEmpty() || parents.get(0) == null) {
            return -1;
        }
        if (!scanIndicatesStellarBody(scan)) {
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
