package org.dce.ed.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.PlanetaryRingBand;

/** Converts journal / EDSM ring payloads into {@link PlanetaryRingBand} rows on {@link BodyInfo}. */
public final class RingGeometryUtil {

    private RingGeometryUtil() {
    }

    public static List<PlanetaryRingBand> fromJournal(List<ScanEvent.RingInfo> rings) {
        if (rings == null || rings.isEmpty()) {
            return List.of();
        }
        List<PlanetaryRingBand> out = new ArrayList<>(rings.size());
        for (ScanEvent.RingInfo ri : rings) {
            if (ri == null || !ri.hasGeometry()) {
                continue;
            }
            out.add(new PlanetaryRingBand(
                    ri.getName(),
                    ri.getRingClass(),
                    ri.getInnerRadM().doubleValue(),
                    ri.getOuterRadM().doubleValue()));
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public static List<PlanetaryRingBand> fromEdsm(List<BodiesResponse.Body.Ring> rings) {
        if (rings == null || rings.isEmpty()) {
            return List.of();
        }
        List<PlanetaryRingBand> out = new ArrayList<>(rings.size());
        for (BodiesResponse.Body.Ring r : rings) {
            if (r == null || r.innerRadius == null || r.outerRadius == null) {
                continue;
            }
            double inner = r.innerRadius.doubleValue();
            double outer = r.outerRadius.doubleValue();
            if (inner <= 0 || outer <= inner) {
                continue;
            }
            String cls = r.type != null ? r.type.trim() : "";
            out.add(new PlanetaryRingBand(r.name, cls.isEmpty() ? null : cls, inner, outer));
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public static boolean hasAccurateDrawGeometry(BodyInfo body) {
        return !sortedBands(body).isEmpty();
    }

    public static List<PlanetaryRingBand> sortedBands(BodyInfo body) {
        if (body == null || body.getPlanetaryRingBands().isEmpty()) {
            return List.of();
        }
        List<PlanetaryRingBand> bands = new ArrayList<>();
        for (PlanetaryRingBand b : body.getPlanetaryRingBands()) {
            if (b != null && b.hasGeometry()) {
                bands.add(b);
            }
        }
        if (bands.isEmpty()) {
            return List.of();
        }
        bands.sort(Comparator.comparingDouble(b -> b.innerRadM.doubleValue()));
        return List.copyOf(bands);
    }

    /**
     * Replace bands when incoming journal scan has geometry; supplement from EDSM when local bands are empty.
     */
    public static void mergeBandsInto(BodyInfo info, List<PlanetaryRingBand> incoming, boolean replaceWhenNonEmpty) {
        if (info == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        List<PlanetaryRingBand> withGeometry = new ArrayList<>();
        for (PlanetaryRingBand b : incoming) {
            if (b != null && b.hasGeometry()) {
                withGeometry.add(copy(b));
            }
        }
        if (withGeometry.isEmpty()) {
            return;
        }
        if (replaceWhenNonEmpty || info.getPlanetaryRingBands().isEmpty()) {
            info.setPlanetaryRingBands(withGeometry);
        }
    }

    public static double hostRadiusMetres(BodyInfo body, List<PlanetaryRingBand> bands) {
        if (body != null && body.getRadius() != null && body.getRadius().doubleValue() > 0) {
            return body.getRadius().doubleValue();
        }
        if (bands == null || bands.isEmpty()) {
            return Double.NaN;
        }
        double maxOuter = 0;
        for (PlanetaryRingBand b : bands) {
            if (b != null && b.outerRadM != null) {
                maxOuter = Math.max(maxOuter, b.outerRadM.doubleValue());
            }
        }
        return maxOuter > 0 ? maxOuter * 0.82 : Double.NaN;
    }

    private static PlanetaryRingBand copy(PlanetaryRingBand b) {
        return new PlanetaryRingBand(b.name, b.ringClass, b.innerRadM.doubleValue(), b.outerRadM.doubleValue());
    }
}
