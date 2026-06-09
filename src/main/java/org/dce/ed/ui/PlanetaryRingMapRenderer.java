package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.PlanetaryRingBand;
import org.dce.ed.util.PlanetaryRingWorldGeometry;
import org.dce.ed.util.RingCompositionColors;
import org.dce.ed.util.RingGeometryUtil;

/** Planetary ring art on the system map: icon at wide zoom, journal radii when zoomed in. */
public final class PlanetaryRingMapRenderer {

    private static final Color ICON_RING_OUTER = new Color(255, 45, 45, 245);
    private static final Color ICON_RING_INNER = new Color(255, 95, 95, 230);

    private PlanetaryRingMapRenderer() {
    }

    public static boolean hasAccurateGeometry(BodyInfo body) {
        return RingGeometryUtil.hasAccurateDrawGeometry(body);
    }

    /** Wide zoom: two tilted red ellipses (legacy decor). */
    public static void drawIcon(Graphics2D g2, double sx, double sy, float bodyRadiusPx) {
        drawIcon(g2, sx, sy, bodyRadiusPx, null, null);
    }

    /** Wide zoom decor; uses projected orientation when {@code ctx} is available. */
    public static void drawIcon(Graphics2D g2, double sx, double sy, float bodyRadiusPx,
            BodyInfo body, PlanetaryRingMapDrawContext ctx) {
        if (bodyRadiusPx <= 0f || !Double.isFinite(sx) || !Double.isFinite(sy)) {
            return;
        }
        if (ctx != null && ctx.usable() && body != null) {
            List<PlanetaryRingBand> bands = RingGeometryUtil.sortedBands(body);
            double hostRadiusM = RingGeometryUtil.hostRadiusMetres(body, bands);
            if (Double.isFinite(hostRadiusM) && hostRadiusM > 0) {
                double metresPerPx = hostRadiusM / bodyRadiusPx;
                float outerPx = Math.max(5f, bodyRadiusPx * 2.85f);
                float innerPx = Math.max(3.5f, bodyRadiusPx * 2.05f);
                if (drawProjectedAnnulus(g2, body, ctx, innerPx * metresPerPx, outerPx * metresPerPx, outerPx,
                        ICON_RING_INNER, ICON_RING_OUTER, true)) {
                    return;
                }
            }
        }
        drawLegacyIconEllipses(g2, sx, sy, bodyRadiusPx);
    }

    /** Subsystem detail zoom: annuli from journal / EDSM inner and outer radii. */
    public static void drawAccurate(Graphics2D g2, double sx, double sy, float bodyRadiusPx, BodyInfo body) {
        drawAccurate(g2, sx, sy, bodyRadiusPx, body, null);
    }

    public static void drawAccurate(Graphics2D g2, double sx, double sy, float bodyRadiusPx, BodyInfo body,
            PlanetaryRingMapDrawContext ctx) {
        if (bodyRadiusPx <= 0f || !Double.isFinite(sx) || !Double.isFinite(sy) || body == null) {
            return;
        }
        List<PlanetaryRingBand> bands = RingGeometryUtil.sortedBands(body);
        if (bands.isEmpty()) {
            return;
        }
        double hostRadiusM = RingGeometryUtil.hostRadiusMetres(body, bands);
        if (!Double.isFinite(hostRadiusM) || hostRadiusM <= 0) {
            return;
        }
        double metresPerPx = hostRadiusM / bodyRadiusPx;
        if (!Double.isFinite(metresPerPx) || metresPerPx <= 0) {
            return;
        }

        Composite prevComposite = g2.getComposite();
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            if (ctx != null && ctx.usable()) {
                for (PlanetaryRingBand band : bands) {
                    double innerM = band.innerRadM.doubleValue();
                    double outerM = band.outerRadM.doubleValue();
                    if (outerM <= innerM) {
                        continue;
                    }
                    float innerPx = (float) (innerM / metresPerPx);
                    float outerPx = (float) (outerM / metresPerPx);
                    if (outerPx <= innerPx + 0.35f) {
                        continue;
                    }
                    Color fill = RingCompositionColors.fillForRingClass(band.ringClass);
                    Color stroke = RingCompositionColors.strokeForRingClass(band.ringClass);
                    if (!drawProjectedAnnulus(g2, body, ctx, innerM, outerM, outerPx, fill, stroke, false)) {
                        drawLegacyAnnulus(g2, sx, sy, innerPx, outerPx, body, fill, stroke);
                    }
                }
                return;
            }
            float flatten = flattenFactor(body);
            float cx = (float) sx;
            float cy = (float) sy;
            for (PlanetaryRingBand band : bands) {
                float innerPx = (float) (band.innerRadM.doubleValue() / metresPerPx);
                float outerPx = (float) (band.outerRadM.doubleValue() / metresPerPx);
                if (outerPx <= innerPx + 0.35f) {
                    continue;
                }
                Color fill = RingCompositionColors.fillForRingClass(band.ringClass);
                fillAnnulusLegacy(g2, cx, cy, innerPx, outerPx, flatten, fill);
                g2.setColor(RingCompositionColors.strokeForRingClass(band.ringClass));
                g2.setStroke(new BasicStroke(Math.max(0.65f, outerPx * 0.018f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                drawLegacyEllipse(g2, cx, cy, outerPx, flatten);
            }
        } finally {
            g2.setComposite(prevComposite);
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    private static boolean drawProjectedAnnulus(Graphics2D g2, BodyInfo body, PlanetaryRingMapDrawContext ctx,
            double innerM, double outerM, float outerPxHint, Color fillOrInnerStroke, Color outerStroke,
            boolean iconMode) {
        if (!(outerM > innerM) || innerM <= 0) {
            return false;
        }
        int segments = PlanetaryRingWorldGeometry.segmentsForRadiusPx(outerPxHint);
        double[][] outerLoop = PlanetaryRingWorldGeometry.ringLoopMapView(
                ctx.hostWorldMetres, body, outerM, ctx.proj0, ctx.proj1, ctx.viewTiltDeg, segments);
        double[][] innerLoop = PlanetaryRingWorldGeometry.ringLoopMapView(
                ctx.hostWorldMetres, body, innerM, ctx.proj0, ctx.proj1, ctx.viewTiltDeg, segments);
        if (outerLoop.length < 4 || innerLoop.length < 4) {
            return false;
        }
        Path2D outerPath = mapLoopToScreenPath(outerLoop, ctx);
        if (outerPath == null) {
            return false;
        }
        if (iconMode) {
            Stroke prevStroke = g2.getStroke();
            Color prevColor = g2.getColor();
            try {
                g2.setColor(outerStroke);
                g2.setStroke(new BasicStroke(Math.max(1.05f, outerPxHint * 0.14f), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.draw(outerPath);
                Path2D innerPath = mapLoopToScreenPath(innerLoop, ctx);
                if (innerPath != null) {
                    g2.setColor(fillOrInnerStroke);
                    g2.setStroke(new BasicStroke(Math.max(0.95f, outerPxHint * 0.11f), BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    g2.draw(innerPath);
                }
            } finally {
                g2.setStroke(prevStroke);
                g2.setColor(prevColor);
            }
            return true;
        }
        Path2D innerPath = mapLoopToScreenPath(innerLoop, ctx);
        if (innerPath == null) {
            return false;
        }
        Area area = new Area(outerPath);
        area.subtract(new Area(innerPath));
        g2.setColor(fillOrInnerStroke);
        g2.fill(area);
        g2.setColor(outerStroke);
        g2.setStroke(new BasicStroke(Math.max(0.65f, outerPxHint * 0.018f), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2.draw(outerPath);
        return true;
    }

    private static Path2D mapLoopToScreenPath(double[][] mapLoop, PlanetaryRingMapDrawContext ctx) {
        Path2D path = new Path2D.Double();
        boolean moved = false;
        for (int i = 0; i < mapLoop.length; i++) {
            double wx = mapLoop[i][0];
            double wy = mapLoop[i][1];
            if (!Double.isFinite(wx) || !Double.isFinite(wy)) {
                return null;
            }
            double sx = ctx.mapToScreenX(wx);
            double sy = ctx.mapToScreenY(wy);
            if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                return null;
            }
            if (!moved) {
                path.moveTo(sx, sy);
                moved = true;
            } else {
                path.lineTo(sx, sy);
            }
        }
        if (!moved) {
            return null;
        }
        path.closePath();
        return path;
    }

    private static void drawLegacyIconEllipses(Graphics2D g2, double sx, double sy, float bodyRadiusPx) {
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            float cx = (float) sx;
            float cy = (float) sy;
            float rw = Math.max(5f, bodyRadiusPx * 2.85f);
            float rh = Math.max(2f, bodyRadiusPx * 0.92f);
            g2.setColor(ICON_RING_OUTER);
            g2.setStroke(new BasicStroke(Math.max(1.05f, bodyRadiusPx * 0.14f), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(cx - rw, cy - rh, rw * 2f, rh * 2f));
            g2.setColor(ICON_RING_INNER);
            float rw2 = Math.max(3.5f, bodyRadiusPx * 2.05f);
            float rh2 = Math.max(1.4f, bodyRadiusPx * 0.62f);
            g2.setStroke(new BasicStroke(Math.max(0.95f, bodyRadiusPx * 0.11f), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(cx - rw2, cy - rh2, rw2 * 2f, rh2 * 2f));
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    private static void drawLegacyAnnulus(Graphics2D g2, double sx, double sy, float innerPx, float outerPx,
            BodyInfo body, Color fill, Color stroke) {
        float flatten = flattenFactor(body);
        float cx = (float) sx;
        float cy = (float) sy;
        fillAnnulusLegacy(g2, cx, cy, innerPx, outerPx, flatten, fill);
        g2.setColor(stroke);
        g2.setStroke(new BasicStroke(Math.max(0.65f, outerPx * 0.018f), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        drawLegacyEllipse(g2, cx, cy, outerPx, flatten);
    }

    private static float flattenFactor(BodyInfo body) {
        Double tilt = body.getAxialTilt();
        double deg = tilt != null && Double.isFinite(tilt.doubleValue())
                ? Math.min(89.0, Math.abs(tilt.doubleValue()))
                : 18.0;
        return (float) Math.max(0.14, Math.cos(Math.toRadians(deg)));
    }

    private static void fillAnnulusLegacy(Graphics2D g2, float cx, float cy, float innerR, float outerR, float flatten,
            Color fill) {
        Ellipse2D.Float outer = legacyEllipse(cx, cy, outerR, flatten);
        Ellipse2D.Float inner = legacyEllipse(cx, cy, innerR, flatten);
        Area area = new Area(outer);
        area.subtract(new Area(inner));
        g2.setColor(fill);
        g2.fill(area);
    }

    private static void drawLegacyEllipse(Graphics2D g2, float cx, float cy, float radius, float flatten) {
        g2.draw(legacyEllipse(cx, cy, radius, flatten));
    }

    private static Ellipse2D.Float legacyEllipse(float cx, float cy, float radius, float flatten) {
        float ry = radius * flatten;
        return new Ellipse2D.Float(cx - radius, cy - ry, radius * 2f, ry * 2f);
    }
}
