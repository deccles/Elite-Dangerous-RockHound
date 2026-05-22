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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

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
    /** Collapsed-summary placeholder map key → collapsed parent map key. */
    private final Map<Integer, Integer> collapsedPlaceholderParentKey = new HashMap<>();
    private final Set<Integer> collapsedKeys = new HashSet<>();
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
                if (e.isPopupTrigger()) {
                    showNodeContextMenu(e);
                    return;
                }
                dragStart = e.getPoint();
                panStartX = panX;
                panStartY = panY;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showNodeContextMenu(e);
                    return;
                }
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
        this.collapsedKeys.clear();
        this.nodeBounds.clear();
        this.collapsedPlaceholderParentKey.clear();
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
        SystemMapHierarchyBuilder.applyLayout(graph, fm, NODE_PAD_X, MIN_NODE_W, MIN_NODE_H, SIBLING_GAP,
                collapsedKeys);
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

    private void showNodeContextMenu(MouseEvent e) {
        if (graph == null) {
            return;
        }
        Integer key = hitTest(e.getX(), e.getY());
        if (key == null) {
            return;
        }
        if (SystemMapHierarchyBuilder.isCollapsedPlaceholderMapKey(key.intValue())) {
            key = Integer.valueOf(resolveCollapseTargetKey(key.intValue()));
        }
        final Integer menuKey = key;
        Node node = graph.nodeByKey.get(menuKey);
        if (node == null || node.children.isEmpty()) {
            return;
        }
        boolean collapsed = collapsedKeys.contains(menuKey);
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(EdoUi.User.PANEL_BG);
        JMenuItem collapseItem = new JMenuItem("Collapse children");
        collapseItem.setBackground(EdoUi.User.PANEL_BG);
        collapseItem.setForeground(EdoUi.User.MAIN_TEXT);
        collapseItem.setEnabled(!collapsed);
        collapseItem.addActionListener(ev -> {
            collapsedKeys.add(menuKey);
            fitToGraph();
        });
        JMenuItem expandItem = new JMenuItem("Expand children");
        expandItem.setBackground(EdoUi.User.PANEL_BG);
        expandItem.setForeground(EdoUi.User.MAIN_TEXT);
        expandItem.setEnabled(collapsed);
        expandItem.addActionListener(ev -> {
            collapsedKeys.remove(menuKey);
            fitToGraph();
        });
        menu.add(collapseItem);
        menu.add(expandItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
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
        SystemMapHierarchyBuilder.applyLayout(graph, fm, NODE_PAD_X, MIN_NODE_W, MIN_NODE_H, SIBLING_GAP,
                collapsedKeys);
        nodeBounds.clear();
        collapsedPlaceholderParentKey.clear();
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
        paintEdgesRecursive(g2, graph.root);
    }

    private void paintEdgesRecursive(Graphics2D g2, Node parent) {
        for (Node child : SystemMapHierarchyBuilder.visibleChildren(parent, collapsedKeys)) {
            paintEdgeBetween(g2, parent, child);
            if (!SystemMapHierarchyBuilder.isCollapsedPlaceholderMapKey(child.mapKey)) {
                paintEdgesRecursive(g2, child);
            }
        }
    }

    private void paintEdgeBetween(Graphics2D g2, Node parent, Node child) {
        Rectangle2D.Double parentRect = nodeBounds.get(Integer.valueOf(parent.mapKey));
        Rectangle2D.Double childRect = nodeBounds.get(Integer.valueOf(child.mapKey));
        if (parentRect == null || childRect == null) {
            return;
        }
        double x1 = parentRect.getCenterX();
        double y1 = parentRect.getMaxY();
        double x2 = childRect.getCenterX();
        double y2 = childRect.getMinY();
        Path2D path = new Path2D.Double();
        path.moveTo(x1, y1);
        double midY = (y1 + y2) / 2.0;
        path.curveTo(x1, midY, x2, midY, x2, y2);
        g2.draw(path);
    }

    private int resolveCollapseTargetKey(int mapKey) {
        Integer parent = collapsedPlaceholderParentKey.get(Integer.valueOf(mapKey));
        return parent != null ? parent.intValue() : mapKey;
    }

    private void paintNodes(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics(getFont());
        paintNodeRecursive(g2, graph.root, fm);
    }

    private void paintNodeRecursive(Graphics2D g2, Node node, FontMetrics fm) {
        Node positioned = graph.nodeByKey.get(Integer.valueOf(node.mapKey));
        if (positioned == null) {
            positioned = node;
        }
        Rectangle2D.Double rect = nodeBounds.get(Integer.valueOf(positioned.mapKey));
        if (rect == null) {
            return;
        }
        boolean hover = hoverKey != null && hoverKey.intValue() == positioned.mapKey;
        g2.setColor(fillFor(positioned.kind, hover));
        g2.fillRoundRect((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height, 10, 10);
        g2.setColor(borderFor(positioned.kind, hover));
        g2.setStroke(new BasicStroke(hover ? 2.5f : 1.5f));
        g2.drawRoundRect((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height, 10, 10);

        boolean placeholder = positioned.kind == NodeKind.COLLAPSED_PLACEHOLDER;
        boolean darkText = usesDarkNodeText(positioned.kind);
        Color labelColor = darkText ? Color.BLACK : EdoUi.User.MAIN_TEXT;
        Color subtitleColor = darkText ? new Color(0, 0, 0, 210) : EdoUi.Internal.mainTextAlpha(200);
        Color parentsColor = darkText ? new Color(0, 0, 0, 175) : EdoUi.Internal.mainTextAlpha(160);
        int labelW = fm.stringWidth(positioned.label);
        int lineCount = 1;
        if (positioned.subtitle != null && !positioned.subtitle.isEmpty()) {
            lineCount++;
        }
        if (positioned.parentsLine != null && !positioned.parentsLine.isEmpty()) {
            lineCount++;
        }
        int ty = (int) rect.getCenterY() - (lineCount - 1) * (fm.getHeight() / 2);
        int tx = (int) (rect.getCenterX() - labelW / 2.0);
        if (placeholder) {
            g2.setFont(getFont().deriveFont(Font.ITALIC));
            fm = g2.getFontMetrics();
            labelW = fm.stringWidth(positioned.label);
            tx = (int) (rect.getCenterX() - labelW / 2.0);
        }
        g2.setColor(labelColor);
        g2.drawString(positioned.label, tx, ty);
        int lineY = ty;
        Font small = getFont().deriveFont(Font.PLAIN, SMALL_FONT_PT);
        if (positioned.subtitle != null && !positioned.subtitle.isEmpty()) {
            g2.setFont(small);
            FontMetrics sfm = g2.getFontMetrics();
            lineY += sfm.getHeight() + TEXT_LINE_GAP;
            int stx = (int) (rect.getCenterX() - sfm.stringWidth(positioned.subtitle) / 2.0);
            g2.setColor(subtitleColor);
            g2.drawString(positioned.subtitle, stx, lineY);
        }
        if (positioned.parentsLine != null && !positioned.parentsLine.isEmpty()) {
            g2.setFont(small);
            FontMetrics pfm = g2.getFontMetrics();
            lineY += pfm.getHeight() + TEXT_LINE_GAP;
            int ptx = (int) (rect.getCenterX() - pfm.stringWidth(positioned.parentsLine) / 2.0);
            g2.setColor(parentsColor);
            g2.drawString(positioned.parentsLine, ptx, lineY);
        }
        if (positioned.subtitle != null || positioned.parentsLine != null) {
            g2.setFont(getFont());
        }
        for (Node child : SystemMapHierarchyBuilder.visibleChildren(positioned, collapsedKeys)) {
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
            case COLLAPSED_PLACEHOLDER -> EdoUi.withAlpha(new Color(70, 85, 105), alpha);
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
        Node positioned = graph != null ? graph.nodeByKey.get(Integer.valueOf(node.mapKey)) : null;
        if (positioned == null) {
            positioned = node;
        }
        int lineStep = fm.getHeight() + TEXT_LINE_GAP;
        int parW = positioned.parentsLine != null ? fm.stringWidth(positioned.parentsLine) : 0;
        int w = positioned.layoutW > 0 ? positioned.layoutW
                : Math.max(MIN_NODE_W, Math.max(fm.stringWidth(positioned.label),
                        Math.max(positioned.subtitle != null ? fm.stringWidth(positioned.subtitle) : 0, parW))
                        + 2 * NODE_PAD_X);
        int h = positioned.layoutH > 0 ? positioned.layoutH : MIN_NODE_H;
        int extra = 0;
        if (positioned.subtitle != null && !positioned.subtitle.isEmpty()) {
            extra += lineStep;
        }
        if (positioned.parentsLine != null && !positioned.parentsLine.isEmpty()) {
            extra += lineStep;
        }
        if (h <= MIN_NODE_H + extra) {
            h = MIN_NODE_H + extra;
        }
        double x = positioned.layoutX - w / 2.0;
        double y = positioned.layoutY - h / 2.0;
        nodeBounds.put(Integer.valueOf(positioned.mapKey), new Rectangle2D.Double(x, y, w, h));
        for (Node child : SystemMapHierarchyBuilder.visibleChildren(node, collapsedKeys)) {
            if (SystemMapHierarchyBuilder.isCollapsedPlaceholderMapKey(child.mapKey)) {
                collapsedPlaceholderParentKey.put(Integer.valueOf(child.mapKey),
                        Integer.valueOf(node.mapKey));
            }
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
        for (Node child : SystemMapHierarchyBuilder.visibleChildren(node, collapsedKeys)) {
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
