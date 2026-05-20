package org.dce.ed.systemmap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;

/**
 * Debug dump: every map body and which orbit polylines are attached (including synthetic schematic rings).
 */
public final class SystemMapOrbitStrokePrinter {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private SystemMapOrbitStrokePrinter() {
    }

    public static void printToConsole(String systemName,
            Map<Integer, BodyInfo> bodies,
            SystemMapModel model,
            List<OrbitPolylineWorldXY> polylines,
            MapScaleMode scaleMode,
            boolean orbitPlaybackActive) {
        printToConsole(systemName, bodies, model, polylines, scaleMode, orbitPlaybackActive, System.out);
    }

    public static void printToConsole(String systemName,
            Map<Integer, BodyInfo> bodies,
            SystemMapModel model,
            List<OrbitPolylineWorldXY> polylines,
            MapScaleMode scaleMode,
            boolean orbitPlaybackActive,
            PrintStream out) {
        if (out == null) {
            return;
        }
        String name = systemName != null && !systemName.isBlank() ? systemName : "(unknown system)";
        List<OrbitPolylineWorldXY> polys = polylines != null ? polylines : List.of();
        out.println(String.format(Locale.US, "[EDO][OrbitMap][Print] system=%s scale=%s playback=%s polylines=%d",
                name, scaleMode != null ? scaleMode : "?", orbitPlaybackActive, polys.size()));
        if (bodies == null || bodies.isEmpty()) {
            out.println("  (no bodies — map empty)");
            return;
        }
        Map<Integer, List<PolylineAttachment>> byBody = new LinkedHashMap<>();
        Set<Integer> drawableBodyIds = new LinkedHashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().isScanBarycentreRow()) {
                continue;
            }
            drawableBodyIds.add(e.getKey());
            byBody.put(e.getKey(), new ArrayList<>());
        }
        List<PolylineAttachment> unassigned = new ArrayList<>();
        int polyIndex = 0;
        for (OrbitPolylineWorldXY poly : polys) {
            if (poly == null) {
                continue;
            }
            PolylineAttachment att = describePolyline(poly, polyIndex++, bodies, model);
            Set<Integer> targets = resolveAttachmentBodyIds(att, bodies, model);
            if (targets.isEmpty()) {
                unassigned.add(att);
            } else {
                for (Integer bid : targets) {
                    byBody.computeIfAbsent(bid, k -> new ArrayList<>()).add(att);
                }
            }
        }
        List<Integer> sortedIds = new ArrayList<>(drawableBodyIds);
        sortedIds.sort(bodyLabelComparator(bodies));
        for (int bodyId : sortedIds) {
            BodyInfo b = bodies.get(Integer.valueOf(bodyId));
            String label = formatBodyLabel(b, bodyId);
            List<PolylineAttachment> attached = byBody.getOrDefault(Integer.valueOf(bodyId), List.of());
            if (attached.isEmpty()) {
                out.println(String.format(Locale.US, "  body %s id=%d — (no orbit strokes)", label, bodyId));
                continue;
            }
            out.println(String.format(Locale.US, "  body %s id=%d — %d stroke(s):", label, bodyId, attached.size()));
            for (PolylineAttachment att : attached) {
                out.println("    " + att.formatLine());
            }
        }
        if (!unassigned.isEmpty()) {
            out.println(String.format(Locale.US, "  unassigned polylines (%d):", unassigned.size()));
            for (PolylineAttachment att : unassigned) {
                out.println("    " + att.formatLine());
            }
        }
        if (model != null) {
            out.println(String.format(Locale.US,
                    "  model: hasBarycentreMutualRing=%s schematicBranchRings=%d pipelinePolylines=%d",
                    model.hasBarycentreMutualRing(), model.schematicBranchRingCount(),
                    model.orbitPolylines().size()));
            if (polys.size() != model.orbitPolylines().size()) {
                out.println(String.format(Locale.US,
                        "  note: panel active polylines=%d differ from pipeline build=%d (zoom/playback rebuild)",
                        polys.size(), model.orbitPolylines().size()));
            }
        }
    }

    private static Set<Integer> resolveAttachmentBodyIds(PolylineAttachment att,
            Map<Integer, BodyInfo> bodies,
            SystemMapModel model) {
        Set<Integer> out = new LinkedHashSet<>();
        int polyBodyId = att.polylineBodyId;
        if (polyBodyId > 0) {
            out.add(Integer.valueOf(polyBodyId));
            return out;
        }
        if (polyBodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID && model != null) {
            out.addAll(model.wideBinarySystemBarycentreStarIds());
            return out;
        }
        if (SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(polyBodyId)) {
            int nullId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - polyBodyId;
            collectBodiesWithImmediateParent(bodies, nullId, out);
            return out;
        }
        if (SystemOrbitGeometry.isPlanetBinaryOuterBarycentreOrbitRingBodyId(polyBodyId)) {
            int nullId = SystemOrbitGeometry.PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE - polyBodyId;
            collectBodiesWithImmediateParent(bodies, nullId, out);
            return out;
        }
        if (att.schematicStarId >= 0) {
            out.add(Integer.valueOf(att.schematicStarId));
            return out;
        }
        if (att.schematicBarycentreMapKey >= 0) {
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(att.schematicBarycentreMapKey)) {
                int nullId = SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(
                        att.schematicBarycentreMapKey);
                collectBodiesWithImmediateParent(bodies, nullId, out);
            }
            return out;
        }
        if (polyBodyId == SystemOrbitGeometry.HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID && bodies != null) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() != null && e.getValue().getStarType() != null
                        && SystemOrbitGeometry.isHierarchicalWideBinary(bodies)) {
                    int id = e.getKey().intValue();
                    if (id != SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies)) {
                        out.add(Integer.valueOf(id));
                    }
                }
            }
        }
        return out;
    }

    private static void collectBodiesWithImmediateParent(Map<Integer, BodyInfo> bodies, int nullParentId,
            Set<Integer> out) {
        if (bodies == null) {
            return;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (e.getValue().getImmediateParentBodyId() == nullParentId) {
                out.add(e.getKey());
            }
        }
    }

    /** Matches {@link SystemOrbitGeometry} {@code SINGLE_STAR_SCHEMATIC_RING_ID_BASE} (package-private). */
    private static final int SCHEMATIC_CONCENTRIC_RING_ID_BASE = -4_000;

    /**
     * Classification and ids for map click hit logging ({@link org.dce.ed.ui.SystemPlanMapPanel}).
     */
    public static final class OrbitStrokeHitInfo {
        public final int polylineBodyId;
        public final String type;
        public final boolean estimated;
        public final int parentBodyId;
        public final String curveKey;

        public OrbitStrokeHitInfo(int polylineBodyId, String type, boolean estimated, int parentBodyId,
                String curveKey) {
            this.polylineBodyId = polylineBodyId;
            this.type = type != null ? type : "?";
            this.estimated = estimated;
            this.parentBodyId = parentBodyId;
            this.curveKey = curveKey;
        }
    }

    public static OrbitStrokeHitInfo orbitStrokeHitInfo(OrbitPolylineWorldXY poly, Map<Integer, BodyInfo> bodies,
            SystemMapModel model) {
        if (poly == null) {
            return new OrbitStrokeHitInfo(0, "?", false, -1, null);
        }
        PolylineAttachment att = describePolyline(poly, 0, bodies, model);
        return new OrbitStrokeHitInfo(att.polylineBodyId, att.type, att.estimated, att.parentBodyId, att.curveKey);
    }

    private static PolylineAttachment describePolyline(OrbitPolylineWorldXY poly, int index,
            Map<Integer, BodyInfo> bodies, SystemMapModel model) {
        PolylineAttachment att = new PolylineAttachment();
        att.polylineBodyId = poly.bodyId;
        att.estimated = poly.estimated;
        att.index = index;
        decodeSchematicRingIds(poly.bodyId, att);
        att.type = classifyPolylineType(poly, bodies);
        att.parentBodyId = resolvePolylineParentId(poly, bodies, model, att);
        att.curveKey = curveKeyHint(poly, bodies, att.parentBodyId);
        computePolylineMetrics(poly, att);
        return att;
    }

    private static String classifyPolylineType(OrbitPolylineWorldXY poly, Map<Integer, BodyInfo> bodies) {
        int id = poly.bodyId;
        if (id == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
            return "binary-barycentre-mutual-ring";
        }
        if (id == SystemOrbitGeometry.HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID) {
            return "hierarchical-inner-stellar-pair";
        }
        if (id == SystemOrbitGeometry.HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID) {
            return "hierarchical-triple-trunk";
        }
        if (SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(id)) {
            return "planet-binary-mutual-ring";
        }
        if (SystemOrbitGeometry.isPlanetBinaryOuterBarycentreOrbitRingBodyId(id)) {
            return "planet-binary-outer-ring";
        }
        if (id <= SCHEMATIC_CONCENTRIC_RING_ID_BASE && id > SCHEMATIC_CONCENTRIC_RING_ID_BASE - 50_000) {
            return "schematic-concentric-ring";
        }
        if (id > 0) {
            BodyInfo b = bodies != null ? bodies.get(Integer.valueOf(id)) : null;
            if (poly.estimated) {
                return "circular-fallback";
            }
            if (b != null && b.getSemiMajorAxisM() != null && b.getSemiMajorAxisM().doubleValue() > 0) {
                Double e = b.getEccentricity();
                if (e != null && e.doubleValue() > 0.02) {
                    return "kepler-ellipse";
                }
            }
            if (attLooksElliptical(poly)) {
                return "kepler-ellipse";
            }
            return "circular-fallback";
        }
        return "synthetic-id-" + id;
    }

    private static boolean attLooksElliptical(OrbitPolylineWorldXY poly) {
        if (poly.wx == null || poly.wy == null || poly.wx.length < 8) {
            return false;
        }
        double cx = 0.0;
        double cy = 0.0;
        int n = Math.min(poly.wx.length, poly.wy.length);
        for (int i = 0; i < n; i++) {
            cx += poly.wx[i];
            cy += poly.wy[i];
        }
        cx /= n;
        cy /= n;
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < n; i++) {
            double r = Math.hypot(poly.wx[i] - cx, poly.wy[i] - cy);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        return maxR > minR * 1.08;
    }

    private static void decodeSchematicRingIds(int ringBodyId, PolylineAttachment att) {
        if (ringBodyId > SCHEMATIC_CONCENTRIC_RING_ID_BASE
                || ringBodyId <= SCHEMATIC_CONCENTRIC_RING_ID_BASE - 50_000) {
            return;
        }
        int encoded = SCHEMATIC_CONCENTRIC_RING_ID_BASE - ringBodyId;
        int hubOrStar = encoded / 100_000;
        att.schematicRadiusLs = encoded % 100_000;
        if (hubOrStar > 0) {
            att.schematicStarId = hubOrStar;
        } else if (hubOrStar < 0) {
            att.schematicBarycentreMapKey = -hubOrStar;
        }
    }

    private static int resolvePolylineParentId(OrbitPolylineWorldXY poly, Map<Integer, BodyInfo> bodies,
            SystemMapModel model, PolylineAttachment att) {
        if (poly.bodyId > 0 && model != null) {
            return model.resolveParentBodyId(poly.bodyId);
        }
        if (poly.bodyId > 0 && bodies != null) {
            BodyInfo b = bodies.get(Integer.valueOf(poly.bodyId));
            if (b != null) {
                return SystemOrbitGeometry.resolveOrbitParentBodyId(b, bodies, poly.bodyId);
            }
        }
        if (att.schematicStarId >= 0) {
            return att.schematicStarId;
        }
        if (poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
            return -1;
        }
        if (SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(poly.bodyId)) {
            int nullId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - poly.bodyId;
            return SystemOrbitGeometry.planetBinaryBarycentreMapKey(nullId);
        }
        return -1;
    }

    private static String curveKeyHint(OrbitPolylineWorldXY poly, Map<Integer, BodyInfo> bodies, int parentBodyId) {
        if (poly.bodyId > 0 && bodies != null) {
            BodyInfo b = bodies.get(Integer.valueOf(poly.bodyId));
            if (b != null) {
                boolean kepler = b.getSemiMajorAxisM() != null && b.getSemiMajorAxisM().doubleValue() > 0;
                if (kepler) {
                    return "K:" + parentBodyId + ":" + poly.bodyId;
                }
            }
            return "C:" + parentBodyId + ":" + poly.bodyId;
        }
        return "S:" + poly.bodyId;
    }

    private static void computePolylineMetrics(OrbitPolylineWorldXY poly, PolylineAttachment att) {
        if (poly.wx == null || poly.wy == null) {
            return;
        }
        int n = Math.min(poly.wx.length, poly.wy.length);
        if (n <= 0) {
            return;
        }
        att.vertexCount = n;
        double sumX = 0.0;
        double sumY = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += poly.wx[i];
            sumY += poly.wy[i];
        }
        att.centreX = sumX / n;
        att.centreY = sumY / n;
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        double sumR = 0.0;
        for (int i = 0; i < n; i++) {
            double r = Math.hypot(poly.wx[i] - att.centreX, poly.wy[i] - att.centreY);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            sumR += r;
        }
        att.meanRadiusLs = sumR / n / LS;
        att.minRadiusLs = minR / LS;
        att.maxRadiusLs = maxR / LS;
    }

    private static String formatBodyLabel(BodyInfo b, int mapKey) {
        if (b == null) {
            return "?" + mapKey;
        }
        String s = b.getShortName();
        if (s == null || s.isBlank()) {
            s = b.getBodyName();
        }
        if (s == null || s.isBlank()) {
            int id = b.getBodyId() >= 0 ? b.getBodyId() : mapKey;
            return "#" + id;
        }
        if (SystemOrbitGeometry.isPrimaryStarBodyByName(b)) {
            return "*";
        }
        return s;
    }

    private static Comparator<Integer> bodyLabelComparator(Map<Integer, BodyInfo> bodies) {
        return (a, b) -> {
            BodyInfo ba = bodies.get(a);
            BodyInfo bb = bodies.get(b);
            boolean starA = ba != null && ba.getStarType() != null;
            boolean starB = bb != null && bb.getStarType() != null;
            if (starA != starB) {
                return starA ? -1 : 1;
            }
            String sa = formatBodyLabel(ba, a.intValue());
            String sb = formatBodyLabel(bb, b.intValue());
            return sa.compareTo(sb);
        };
    }

    private static final class PolylineAttachment {
        int index;
        int polylineBodyId;
        String type;
        boolean estimated;
        int parentBodyId = -1;
        String curveKey;
        int vertexCount;
        double centreX;
        double centreY;
        double meanRadiusLs = Double.NaN;
        double minRadiusLs = Double.NaN;
        double maxRadiusLs = Double.NaN;
        int schematicStarId = -1;
        int schematicBarycentreMapKey = -1;
        int schematicRadiusLs = -1;

        String formatLine() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "[%d] polylineBodyId=%d type=%s estimated=%s",
                    index, polylineBodyId, type, estimated));
            if (parentBodyId >= 0) {
                sb.append(String.format(Locale.US, " parentBodyId=%d", parentBodyId));
            }
            if (curveKey != null) {
                sb.append(" curveKey=").append(curveKey);
            }
            if (vertexCount > 0 && Double.isFinite(centreX)) {
                sb.append(String.format(Locale.US, " centre=(%.1f, %.1f) m", centreX, centreY));
            }
            if (Double.isFinite(meanRadiusLs)) {
                sb.append(String.format(Locale.US, " radiusLs mean=%.2f min=%.2f max=%.2f",
                        meanRadiusLs, minRadiusLs, maxRadiusLs));
            }
            if (schematicStarId >= 0) {
                sb.append(String.format(Locale.US, " schematicAtStarId=%d schematicRadiusLs=%d",
                        schematicStarId, schematicRadiusLs));
            }
            if (schematicBarycentreMapKey >= 0) {
                sb.append(String.format(Locale.US, " schematicAtBarycentreKey=%d schematicRadiusLs=%d",
                        schematicBarycentreMapKey, schematicRadiusLs));
            }
            return sb.toString();
        }
    }
}
