package org.dce.ed.systemmap;

import java.util.List;

/**
 * Outcome of topology rules: what kind of system this is and which bodies play special roles.
 */
public record SystemMapClassification(
        SystemLayoutKind layoutKind,
        int mapStellarCount,
        int primaryAnchorBodyId,
        int centralStarId,
        List<Integer> barycentricStarIds) {

    public boolean wideBinary() {
        return layoutKind == SystemLayoutKind.WIDE_BINARY;
    }

    public boolean singleStar() {
        return layoutKind == SystemLayoutKind.SINGLE_STAR;
    }
}
