package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.dce.ed.OverlayPreferences;

/**
 * Table header that paints only the header renderer (no LAF background),
 * so pass-through transparency shows the backing color.
 */
public final class TransparentTableHeader extends JTableHeader {

    private static final long serialVersionUID = 1L;

    public TransparentTableHeader(TableColumnModel cm) {
        super(cm);
        setOpaque(false);
        setBackground(EdoUi.Internal.TRANSPARENT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        TableColumnModel cm = getColumnModel();
        int n = cm.getColumnCount();
        if (n <= 0) return;
        javax.swing.JTable tbl = getTable();
        TableCellRenderer renderer = getDefaultRenderer();
        if (renderer == null || tbl == null) return;
        boolean ltr = getComponentOrientation().isLeftToRight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        for (int i = 0; i < n; i++) {
            int col = ltr ? i : (n - 1 - i);
            TableColumn tc = cm.getColumn(col);
            TableCellRenderer colRenderer = tc.getHeaderRenderer();
            if (colRenderer == null) colRenderer = renderer;
            java.awt.Rectangle r = getHeaderRect(col);
            // CLEAR composites to garbage (often lime green) on decorated JFrames; only use on pass-through overlay.
            if (OverlayPreferences.overlayChromeRequestsTransparency()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                g2.fillRect(r.x, r.y, r.width, r.height);
            } else {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(EdoUi.User.BACKGROUND);
                g2.fillRect(r.x, r.y, r.width, r.height);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            java.awt.Component cell = colRenderer.getTableCellRendererComponent(tbl, tc.getHeaderValue(), false, false, -1, col);
            cell.setBounds(0, 0, r.width, r.height);
            Graphics2D cellG = (Graphics2D) g2.create(r.x, r.y, r.width, r.height);
            cell.paint(cellG);
            cellG.dispose();
        }

        // When the header is wider than the sum of column widths (viewport wider than table),
        // Swing leaves an unpainted strip — often shows as a bright white bar. Fill gaps.
        paintHeaderGaps(g2, n, ltr);

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(EdoUi.ED_ORANGE_TRANS);
        int y = getHeight() - 1;
        g2.drawLine(0, y, getWidth(), y);
        g2.dispose();
    }

    /**
     * Paints regions of the header row not covered by any column header cell (leading/trailing gaps).
     */
    private void paintHeaderGaps(Graphics2D g2, int n, boolean ltr) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        Rectangle first = getHeaderRect(ltr ? 0 : n - 1);
        Rectangle last = getHeaderRect(ltr ? n - 1 : 0);

        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();

        // Leading gap (e.g. RTL or odd layout)
        if (first.x > 0) {
            fillHeaderGap(g2, 0, 0, first.x, h, transparent);
        }
        // Trailing gap (common: narrow table in a wide scroll pane)
        int lastRight = last.x + last.width;
        if (lastRight < w) {
            fillHeaderGap(g2, lastRight, 0, w - lastRight, h, transparent);
        }
    }

    private static void fillHeaderGap(Graphics2D g2, int x, int y, int width, int height, boolean transparent) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (transparent) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fillRect(x, y, width, height);
        } else {
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(EdoUi.User.BACKGROUND);
            g2.fillRect(x, y, width, height);
        }
    }
}
