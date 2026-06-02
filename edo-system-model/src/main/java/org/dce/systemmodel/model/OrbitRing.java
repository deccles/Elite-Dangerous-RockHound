package org.dce.systemmodel.model;

import java.util.List;

public record OrbitRing(int bodyId, int parentId, List<double[]> pointsMetres) {
}
