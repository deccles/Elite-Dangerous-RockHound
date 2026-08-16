package org.dce.ed.mission;

@FunctionalInterface
public interface TransportCoordinateResolver {
    double[] resolve(String systemName) throws Exception;
}
