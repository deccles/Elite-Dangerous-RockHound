package org.dce.ed.systemmap.display;

import java.util.List;
import java.util.Set;

public record MapRenderPlan(
        List<MapBodyDraw> bodies,
        List<MapOrbitDraw> orbits,
        List<MapClusterIcon> clusterIcons,
        Set<Integer> hiddenBodyIds,
        Set<Integer> collapsedLabelBodyIds) {

    public static MapRenderPlan empty() {
        return new MapRenderPlan(List.of(), List.of(), List.of(), Set.of(), Set.of());
    }

    public record MapBodyDraw(int bodyId, double worldX, double worldY, boolean showLabel, boolean definitive) {
    }

    public record MapOrbitDraw(int bodyId, List<double[]> polylineMetres) {
    }

    public record MapClusterIcon(String kind, double worldX, double worldY, List<Integer> memberBodyIds) {
    }
}
