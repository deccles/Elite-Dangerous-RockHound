package org.dce.ed.systemmap.display;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.Position3d;
import org.dce.systemmodel.model.SystemModel;

/**
 * Composes a {@link MapRenderPlan} from true-scale world geometry and viewport (LOD / declutter).
 * Does not mutate positions — clustering is icon/label only.
 */
public final class MapDisplayComposer {

    private final double pixelsPerMetre;
    private final double minLabelSpacingPx;

    public MapDisplayComposer(double pixelsPerMetre, double minLabelSpacingPx) {
        this.pixelsPerMetre = pixelsPerMetre;
        this.minLabelSpacingPx = minLabelSpacingPx;
    }

    public MapRenderPlan compose(SystemModel model, Instant t, double viewportWidthPx, double viewportHeightPx) {
        if (model == null) {
            return MapRenderPlan.empty();
        }
        var subgraph = model.definitiveSubgraph();
        List<MapRenderPlan.MapBodyDraw> bodies = new ArrayList<>();
        Set<Integer> hidden = new HashSet<>();
        Set<Integer> collapsedLabels = new HashSet<>();

        for (int bodyId : subgraph.definitiveBodyIds()) {
            Position3d pos = model.positionAt(bodyId, t);
            boolean showLabel = !labelWouldCollide(bodies, pos.x(), pos.y());
            bodies.add(new MapRenderPlan.MapBodyDraw(bodyId, pos.x(), pos.y(), showLabel, true));
            if (!showLabel) {
                collapsedLabels.add(bodyId);
            }
        }

        List<MapRenderPlan.MapOrbitDraw> orbits = new ArrayList<>();
        for (OrbitRing ring : model.orbitRingsAt(t)) {
            if (!subgraph.definitiveBodyIds().contains(ring.bodyId())) {
                continue;
            }
            orbits.add(new MapRenderPlan.MapOrbitDraw(ring.bodyId(), ring.pointsMetres()));
        }

        List<MapRenderPlan.MapClusterIcon> clusterIcons = new ArrayList<>();
        clusterIcons.addAll(twinRingSubsystemIcons(model, t));
        clusterIcons.addAll(sunClusterIcons(model, t));

        return new MapRenderPlan(bodies, orbits, clusterIcons, hidden, collapsedLabels);
    }

    private List<MapRenderPlan.MapClusterIcon> twinRingSubsystemIcons(SystemModel model, Instant t) {
        List<MapRenderPlan.MapClusterIcon> icons = new ArrayList<>();
        for (BarycentreNode bc : model.barycentres().values()) {
            List<Integer> members = new ArrayList<>();
            for (BodyNode b : model.bodies().values()) {
                if (b.orbitParent() != null
                        && b.orbitParent().type() == org.dce.systemmodel.journal.ParentRef.ParentType.NULL
                        && b.orbitParent().bodyId() == bc.bodyId()) {
                    members.add(b.bodyId());
                }
            }
            if (members.size() >= 2) {
                Position3d center = model.positionAt(bc.bodyId(), t);
                icons.add(new MapRenderPlan.MapClusterIcon(
                        "twin-ring", center.x(), center.y(), List.copyOf(members)));
            }
        }
        return icons;
    }

    private List<MapRenderPlan.MapClusterIcon> sunClusterIcons(SystemModel model, Instant t) {
        List<MapRenderPlan.MapClusterIcon> icons = new ArrayList<>();
        List<Integer> stars = new ArrayList<>();
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() == org.dce.systemmodel.model.BodyKind.STAR && b.definitive()) {
                stars.add(b.bodyId());
            }
        }
        if (stars.size() >= 2) {
            double sx = 0;
            double sy = 0;
            for (int id : stars) {
                Position3d p = model.positionAt(id, t);
                sx += p.x();
                sy += p.y();
            }
            sx /= stars.size();
            sy /= stars.size();
            icons.add(new MapRenderPlan.MapClusterIcon("sun-cluster", sx, sy, List.copyOf(stars)));
        }
        return icons;
    }

    private boolean labelWouldCollide(List<MapRenderPlan.MapBodyDraw> placed, double x, double y) {
        for (MapRenderPlan.MapBodyDraw p : placed) {
            double dx = (p.worldX() - x) * pixelsPerMetre;
            double dy = (p.worldY() - y) * pixelsPerMetre;
            if (dx * dx + dy * dy < minLabelSpacingPx * minLabelSpacingPx) {
                return true;
            }
        }
        return false;
    }
}
