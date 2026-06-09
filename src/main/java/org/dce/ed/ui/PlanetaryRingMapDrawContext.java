package org.dce.ed.ui;

/**
 * Map view state for drawing planetary rings in the same projected frame as orbit polylines and body dots.
 */
public final class PlanetaryRingMapDrawContext {

    public final double[] hostWorldMetres;
    public final int proj0;
    public final int proj1;
    public final int viewTiltDeg;
    public final double viewCenterWx;
    public final double viewCenterWy;
    public final double scale;
    public final double pad;
    public final double availW;
    public final double availH;

    public PlanetaryRingMapDrawContext(
            double[] hostWorldMetres,
            int proj0,
            int proj1,
            int viewTiltDeg,
            double viewCenterWx,
            double viewCenterWy,
            double scale,
            double pad,
            double availW,
            double availH) {
        this.hostWorldMetres = hostWorldMetres;
        this.proj0 = proj0;
        this.proj1 = proj1;
        this.viewTiltDeg = viewTiltDeg;
        this.viewCenterWx = viewCenterWx;
        this.viewCenterWy = viewCenterWy;
        this.scale = scale;
        this.pad = pad;
        this.availW = availW;
        this.availH = availH;
    }

    public boolean usable() {
        return hostWorldMetres != null && hostWorldMetres.length >= 2
                && Double.isFinite(scale) && scale > 0
                && Double.isFinite(availW) && availW > 0
                && Double.isFinite(availH) && availH > 0;
    }

    public double mapToScreenX(double wx) {
        return pad + availW / 2.0 + (wx - viewCenterWx) * scale;
    }

    public double mapToScreenY(double wy) {
        return pad + availH / 2.0 - (wy - viewCenterWy) * scale;
    }
}
