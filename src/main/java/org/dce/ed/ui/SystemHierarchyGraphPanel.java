package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import org.dce.ed.systemmap.SystemMapHierarchyBuilder;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Node;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.NodeKind;

/**
 * Top-down graph of orbital parent links within one star system.
 */
public final class SystemHierarchyGraphPanel extends JPanel {

    private static final float LABEL_FONT_PT = 14f;
    private static final float SMALL_FONT_PT = 12f;
    private static final int TEXT_LINE_GAP = 2;
    private static final int NODE_PAD_X = 12;
    private static final int NODE_PAD_Y = 8;
    private static final int MIN_NODE_W = 84;
    private static final int MIN_NODE_H = 44;
    private static final int SIBLING_GAP = 28;

    private Graph graph;
    private final Map<Integer, Rectangle2D.Double> nodeBounds = new HashMap<>();
    private double scale = 1.0;
    private double panX = 40.0;
    private double panY = 40.0;
    private Point dragStart;
    private double panStartX;
    private double panStartY;
    private Integer hoverKey;
    private Runnable viewChangeListener;

    public SystemHierarchyGraphPanel() {
        setBackground(EdoUi.User.BACKGROUND);
        setPreferredSize(new Dimension(900, 600));
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) LABEL_FONT_PT));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                panStartX = panX;
                panStartY = panY;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean wasDragging = dragStart != null;
                dragStart = null;
                setCursor(Cursor.getDefaultCursor());
                if (wasDragging) {
                    fireViewChanged();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                panX = panStartX + (e.getX() - dragStart.x);
                panY = panStartY + (e.getY() - dragStart.y);
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Integer hit = hitTest(e.getX(), e.getY());
                if (hit == null ? hoverKey != null : !hit.equals(hoverKey)) {
                    hoverKey = hit;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                double mx = e.getX();
                double my = e.getY();
                double wx = (mx - panX) / scale;
                double wy = (my - panY) / scale;
                scale = Math.max(0.25, Math.min(3.0, scale * factor));
                panX = mx - wx * scale;
                panY = my - wy * scale;
                repaint();
                fireViewChanged();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
        this.hoverKey = null;
        this.nodeBounds.clear();
        repaint();
    }

    public void setViewChangeListener(Runnable viewChangeListener) {
        this.viewChangeListener = viewChangeListener;
    }

    public double getScale() {
        return scale;
    }

    public double getPanX() {
        return panX;
    }

    public double getPanY() {
        return panY;
    }

    public void setViewTransform(double scale, double panX, double panY) {
        if (graph == null) {
            return;
        }
        this.scale = Math.max(0.25, Math.min(3.0, scale));
        this.panX = panX;
        this.panY = panY;
        repaint();
    }

    private void fireViewChanged() {
        if (viewChangeListener != null && graph != null) {
            viewChangeListener.run();
        }
    }

    public void fitToGraph() {
        if (graph == null) {
            return;
        }
        FontMetrics fm = getFontMetrics(getFont());
        SystemMapHierarchyBuilder.applyLayout(graph, fm, NODE_PAD_X, MIN_NODE_W, MIN_NODE_H, SIBLING_GAP);
        Rectangle2D bounds = graphBounds(graph.root);
        if (bounds.getWidth() < 1.0 || bounds.getHeight() < 1.0) {
            scale = 1.0;
            panX = 40.0;
            panY = 40.0;
            repaint();
            return;
        }
        double margin = 48.0;
        double w = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        double h = getHeight() > 0 ? getHeight() : getPreferredSize().height;
        double sx = (w - 2 * margin) / bounds.getWidth();
        double sy = (h - 2 * margin) / bounds.getHeight();
        scale = Math.max(0.25, Math.min(2.5, Math.min(sx, sy)));
        panX = margin - bounds.getMinX() * scale;
        panY = margin - bounds.getMinY() * scale;
        repaint();
        fireViewChanged();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (graph == null) {
            g2.setColor(EdoUi.User.MAIN_TEXT);
            g2.drawString("Enter a system name and click Load", 24, 32);
            g2.dispose();
            return;
        }

        FontMetrics fm = g2.getFontMetrics(getFont());
        SystemMapHierarchyBuilder.applyLayout(graph, fm, NODE_PAD_X, MIN_NODE_W, MIN_NODE_H, SIBLING_GAP);
        nodeBounds.clear();
        layoutNodeBounds(graph.root, fm);
        g2.translate(panX, panY);
        g2.scale(scale, scale);
        paintEdges(g2);
        paintNodes(g2);
        g2.dispose();
    }

    private void paintEdges(Graphics2D g2) {
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(EdoUi.Internal.mainTextAlpha(140));
        for (SystemMapHierarchyBuilder.Edge edge : graph.edges) {
            Rectangle2D.Double parent = nodeBounds.get(Integer.valueOf(edge.parentKey));
            Rectangle2D.Double child = nodeBounds.get(Integer.valueOf(edge.childKey));
            if (parent == null || child == null) {
                continue;
            }
            double x1 = parent.getCenterX();
            double y1 = parent.getMaxY();
            double x2 = child.getCenterX();
            double y2 = child.getMinY();
            Path2D path = new Path2D.Double();
            path.moveTo(x1, y1);
            double midY = (y1 + y2) / 2.0;
            path.curveTo(x1, midY, x2, midY, x2, y2);
            g2.draw(path);
        }
    }

    private void paintNodes(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics(getFont());
        paintNodeRecursive(g2, graph.root, fm);
    }

    private void paintNodeRecursive(Graphics2D g2, Node node, FontMetrics fm) {
        Rectangle2D.Double rect = nodeBounds.get(Integer.valueOf(node.mapKey));
        if (rect == null) {
            return;
        }
        boolean hover = hoverKey != null && hoverKey.intValue() == node.mapKey;
        g2.setColor(fillFor(node.kind, hover));
        g2.fillRoundRect((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height, 10, 10);
        g2.setColor(borderFor(node.kind, hover));
        g2.setStroke(new BasicStroke(hover ? 2.5f : 1.5f));
        g2.drawRoundRect((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height, 10, 10);

        boolean darkText = usesDarkNodeText(node.kind);
        Color labelColor = darkText ? Color.BLACK : EdoUi.User.MAIN_TEXT;
        Color subtitleColor = darkText ? new Color(0, 0, 0, 210) : EdoUi.Internal.mainTextAlpha(200);
        Color parentsColor = darkText ? new Color(0, 0, 0, 175) : EdoUi.Internal.mainTextAlpha(160);
        int labelW = fm.stringWidth(node.label);
        int subW = node.subtitle != null ? fm.stringWidth(node.subtitle) : 0;
        int parW = node.parentsLine != null ? fm.stringWidth(node.parentsLine) : 0;
        int textW = Math.max(labelW, Math.max(subW, parW));
        int lineCount = 1;
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            lineCount++;
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            lineCount++;
        }
        int ty = (int) rect.getCenterY() - (lineCount - 1) * (fm.getHeight() / 2);
        int tx = (int) (rect.getCenterX() - labelW / 2.0);
        g2.setColor(labelColor);
        g2.drawString(node.label, tx, ty);
        int lineY = ty;
        Font small = getFont().deriveFont(Font.PLAIN, SMALL_FONT_PT);
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            g2.setFont(small);
            FontMetrics sfm = g2.getFontMetrics();
            lineY += sfm.getHeight() + TEXT_LINE_GAP;
            int stx = (int) (rect.getCenterX() - sfm.stringWidth(node.subtitle) / 2.0);
            g2.setColor(subtitleColor);
            g2.drawString(node.subtitle, stx, lineY);
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            g2.setFont(small);
            FontMetrics pfm = g2.getFontMetrics();
            lineY += pfm.getHeight() + TEXT_LINE_GAP;
            int ptx = (int) (rect.getCenterX() - pfm.stringWidth(node.parentsLine) / 2.0);
            g2.setColor(parentsColor);
            g2.drawString(node.parentsLine, ptx, lineY);
            g2.setFont(getFont());
        } else if (node.subtitle != null && !node.subtitle.isEmpty()) {
            g2.setFont(getFont());
        }
        for (Node child : node.children) {
            paintNodeRecursive(g2, child, fm);
        }
    }

    private static boolean usesDarkNodeText(NodeKind kind) {
        return kind == NodeKind.STAR
                || kind == NodeKind.SYSTEM_BARYCENTRE
                || kind == NodeKind.SCAN_BARYCENTRE
                || kind == NodeKind.PLANET_BINARY_BARYCENTRE;
    }

    private static Color fillFor(NodeKind kind, boolean hover) {
        int alpha = hover ? 230 : 190;
        Color barycentrePurple = new Color(120, 90, 200);
        return switch (kind) {
            case SYSTEM_BARYCENTRE, SCAN_BARYCENTRE, PLANET_BINARY_BARYCENTRE ->
                    EdoUi.withAlpha(barycentrePurple, alpha);
            case STAR -> EdoUi.withAlpha(new Color(220, 170, 40), alpha);
            case PLANET -> EdoUi.withAlpha(new Color(50, 120, 180), alpha);
            case MOON -> EdoUi.withAlpha(new Color(90, 110, 130), alpha);
            case OTHER -> EdoUi.withAlpha(EdoUi.User.PANEL_BG, alpha);
        };
    }

    private static Color borderFor(NodeKind kind, boolean hover) {
        Color base = switch (kind) {
            case STAR -> EdoUi.User.VALUABLE;
            case SYSTEM_BARYCENTRE, SCAN_BARYCENTRE, PLANET_BINARY_BARYCENTRE -> new Color(180, 150, 240);
            default -> EdoUi.User.MAIN_TEXT;
        };
        return hover ? base.brighter() : EdoUi.withAlpha(base, 220);
    }

    private void layoutNodeBounds(Node node, FontMetrics fm) {
        int lineStep = fm.getHeight() + TEXT_LINE_GAP;
        int parW = node.parentsLine != null ? fm.stringWidth(node.parentsLine) : 0;
        int w = node.layoutW > 0 ? node.layoutW
                : Math.max(MIN_NODE_W, Math.max(fm.stringWidth(node.label),
                        Math.max(node.subtitle != null ? fm.stringWidth(node.subtitle) : 0, parW)) + 2 * NODE_PAD_X);
        int h = node.layoutH > 0 ? node.layoutH : MIN_NODE_H;
        int extra = 0;
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            extra += lineStep;
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            extra += lineStep;
        }
        if (h <= MIN_NODE_H + extra) {
            h = MIN_NODE_H + extra;
        }
        double x = node.layoutX - w / 2.0;
        double y = node.layoutY - h / 2.0;
        nodeBounds.put(Integer.valueOf(node.mapKey), new Rectangle2D.Double(x, y, w, h));
        for (Node child : node.children) {
            layoutNodeBounds(child, fm);
        }
    }

    private Rectangle2D graphBounds(Node node) {
        Rectangle2D.Double rect = nodeBounds.get(Integer.valueOf(node.mapKey));
        if (rect == null) {
            layoutNodeBounds(node, getFontMetrics(getFont()));
            rect = nodeBounds.get(Integer.valueOf(node.mapKey));
        }
        Rectangle2D bounds = rect != null ? new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height) : null;
        for (Node child : node.children) {
            Rectangle2D childBounds = graphBounds(child);
            if (bounds == null) {
                bounds = childBounds;
            } else if (childBounds != null) {
                bounds.add(childBounds);
            }
        }
        return bounds != null ? bounds : new Rectangle2D.Double(0, 0, 1, 1);
    }

    private Integer hitTest(int sx, int sy) {
        double wx = (sx - panX) / scale;
        double wy = (sy - panY) / scale;
        for (Map.Entry<Integer, Rectangle2D.Double> e : nodeBounds.entrySet()) {
            if (e.getValue().contains(wx, wy)) {
                return e.getKey();
            }
        }
        return null;
    }
}
