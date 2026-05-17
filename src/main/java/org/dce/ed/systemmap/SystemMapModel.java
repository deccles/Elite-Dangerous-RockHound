package org.dce.ed.systemmap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;

/**
 * Immutable schematic map state for one system: classified layout, projected body positions, and orbit strokes.
 * Built only through {@link SystemMapPipeline} — not by the Swing map panel.
 */
public final class SystemMapModel {

    private final String systemName;
    private final Map<Integer, BodyInfo> bodies;
    private final SystemMapClassification classification;
    private final int projectionAxis0;
    private final int projectionAxis1;
    private final Map<Integer, double[]> positionsMetres;
    private final List<OrbitPolylineWorldXY> orbitPolylines;
    private final WideBinaryFlattenFrame wideBinaryFlattenFrame;

    SystemMapModel(String systemName,
            Map<Integer, BodyInfo> bodies,
            SystemMapClassification classification,
            int projectionAxis0,
            int projectionAxis1,
            Map<Integer, double[]> positionsMetres,
            List<OrbitPolylineWorldXY> orbitPolylines,
            WideBinaryFlattenFrame wideBinaryFlattenFrame) {
        this.systemName = systemName;
        this.bodies = Collections.unmodifiableMap(bodies);
        this.classification = classification;
        this.projectionAxis0 = projectionAxis0;
        this.projectionAxis1 = projectionAxis1;
        this.positionsMetres = Collections.unmodifiableMap(positionsMetres);
        this.orbitPolylines = List.copyOf(orbitPolylines);
        this.wideBinaryFlattenFrame = wideBinaryFlattenFrame;
    }

    public String systemName() {
        return systemName;
    }

    public Map<Integer, BodyInfo> bodies() {
        return bodies;
    }

    public SystemMapClassification classification() {
        return classification;
    }

    public int projectionAxis0() {
        return projectionAxis0;
    }

    public int projectionAxis1() {
        return projectionAxis1;
    }

    public Map<Integer, double[]> positionsMetres() {
        return positionsMetres;
    }

    public List<OrbitPolylineWorldXY> orbitPolylines() {
        return orbitPolylines;
    }

    public WideBinaryFlattenFrame wideBinaryFlattenFrame() {
        return wideBinaryFlattenFrame;
    }

    public double mapPlaneX(int bodyId) {
        double[] p = positionsMetres.get(Integer.valueOf(bodyId));
        if (p == null) {
            return Double.NaN;
        }
        return SystemOrbitGeometry.worldAxisMetres(p, projectionAxis0);
    }

    public double mapPlaneY(int bodyId) {
        double[] p = positionsMetres.get(Integer.valueOf(bodyId));
        if (p == null) {
            return Double.NaN;
        }
        return SystemOrbitGeometry.worldAxisMetres(p, projectionAxis1);
    }

    public boolean hasOrbitRingForBody(int bodyId) {
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == bodyId) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBarycentreMutualRing() {
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                return true;
            }
        }
        return false;
    }

    /** Count of schematic branch rings (synthetic negative body ids from {@link SystemOrbitGeometry}). */
    public int schematicBranchRingCount() {
        int n = 0;
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId < -2) {
                n++;
            }
        }
        return n;
    }
}
