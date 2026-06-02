package org.dce.ed.systemmap;

import java.time.Instant;

import org.dce.ed.systemmap.display.MapDisplayComposer;
import org.dce.ed.systemmap.display.MapRenderPlan;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.state.SystemState;

/**
 * Bridges journal-authoritative system model to map UI via {@link MapRenderPlan}.
 */
public final class MapSceneAdapter {

    private MapSceneAdapter() {
    }

    public static MapRenderPlan renderPlan(
            SystemState state,
            Instant simEpoch,
            double pixelsPerMetre,
            double viewportWidthPx,
            double viewportHeightPx,
            boolean strictBuild) {
        SystemModelService.ModelHandle handle = SystemModelService.rebuild(state, strictBuild);
        if (handle.model() == null) {
            return MapRenderPlan.empty();
        }
        MapDisplayComposer composer = new MapDisplayComposer(pixelsPerMetre, 48.0);
        return composer.compose(handle.model(), simEpoch, viewportWidthPx, viewportHeightPx);
    }
}
