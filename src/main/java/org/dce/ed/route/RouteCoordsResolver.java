package org.dce.ed.route;

/**
 * Resolves approximate star coordinates for synthetic route rows (cache, journal star pos, EDSM, etc.).
 */
@FunctionalInterface
public interface RouteCoordsResolver {
    /**
     * @param preferredStarPos optional 3-vector from Location/FsdJump when this row is current system
     */
    Double[] resolve(String systemName, long systemAddress, double[] preferredStarPos);
}
