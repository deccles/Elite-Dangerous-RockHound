package org.dce.ed.systemmap;

import java.util.List;

/**
 * Outcome of topology rules: what kind of system this is and which bodies play special roles.
 */
public record SystemMapClassification(
        SystemLayoutKind layoutKind,
        int mapStellarCount,
        int primaryAnchorBodyId,
        int schematicCentralStarId,
        List<Integer> barycentricStarIds,
        boolean singleStarSchematicMap) {

    public boolean wideBinary() {
        return layoutKind == SystemLayoutKind.WIDE_BINARY;
    }
}
