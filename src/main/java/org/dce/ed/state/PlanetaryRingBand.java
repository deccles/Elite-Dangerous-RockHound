package org.dce.ed.state;

/**
 * One planetary ring band from journal {@code Rings[]} or EDSM {@code rings[]}.
 * Radii are metres from the host body's centre.
 */
public class PlanetaryRingBand {

    public String name;
    public String ringClass;
    public Double innerRadM;
    public Double outerRadM;

    public PlanetaryRingBand() {
    }

    public PlanetaryRingBand(String name, String ringClass, double innerRadM, double outerRadM) {
        this.name = name;
        this.ringClass = ringClass;
        this.innerRadM = Double.valueOf(innerRadM);
        this.outerRadM = Double.valueOf(outerRadM);
    }

    public boolean hasGeometry() {
        return innerRadM != null && outerRadM != null
                && innerRadM.doubleValue() > 0
                && outerRadM.doubleValue() > innerRadM.doubleValue();
    }
}
