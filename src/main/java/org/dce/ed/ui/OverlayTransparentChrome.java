package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Component;
import java.awt.Container;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;

import org.dce.ed.OverlayPreferences;

/**
 * Recursively clears LAF default white/opaque chrome on overlay tab content.
 */
public final class OverlayTransparentChrome {

    private OverlayTransparentChrome() {
    }

    public static void applyToSubtree(Component root) {
        if (root == null) {
            return;
        }
        if (!(root instanceof JComponent jc)) {
            if (root instanceof Container container) {
                for (Component child : container.getComponents()) {
                    applyToSubtree(child);
                }
            }
            return;
        }

        if (root instanceof JScrollPane scroll) {
            configureScrollPane(scroll);
        } else if (root instanceof JSplitPane split) {
            split.setOpaque(false);
            split.setBorder(null);
            split.setBackground(EdoUi.Internal.TRANSPARENT);
            EdoMiningSplitPaneUi.applyDividerTheme(split);
        } else if (root instanceof JTable table) {
            table.setOpaque(false);
            table.setBackground(EdoUi.Internal.TRANSPARENT);
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency(header));
                header.setBackground(OverlayPreferences.overlayChromeRequestsTransparency(header)
                        ? EdoUi.Internal.TRANSPARENT
                        : EdoUi.User.BACKGROUND);
            }
        } else if (shouldClearWhiteChrome(jc)) {
            jc.setOpaque(false);
            jc.setBackground(EdoUi.Internal.TRANSPARENT);
        }

        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyToSubtree(child);
            }
        }
    }

    public static void configureScrollPane(JScrollPane scroll) {
        if (scroll == null) {
            return;
        }
        scroll.setUI(TransparentScrollPaneUI.createUI(scroll));
        scroll.setOpaque(false);
        scroll.setBackground(EdoUi.Internal.TRANSPARENT);
        scroll.setBorder(null);
        scroll.setViewportBorder(null);

        JViewport viewport = scroll.getViewport();
        if (viewport != null) {
            viewport.setOpaque(false);
            viewport.setBackground(EdoUi.Internal.TRANSPARENT);
            viewport.setBorder(null);
            viewport.setUI(TransparentViewportUI.createUI(viewport));
        }

        JViewport columnHeader = scroll.getColumnHeader();
        if (columnHeader != null) {
            columnHeader.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            columnHeader.setOpaque(false);
            columnHeader.setBackground(EdoUi.Internal.TRANSPARENT);
            columnHeader.setBorder(null);
            columnHeader.setUI(TransparentViewportUI.createUI(columnHeader));
        }

        JScrollBar vsb = scroll.getVerticalScrollBar();
        if (vsb != null) {
            vsb.setOpaque(false);
            vsb.setBackground(EdoUi.Internal.TRANSPARENT);
            vsb.setUI(new SubtleScrollBarUI());
        }
        JScrollBar hsb = scroll.getHorizontalScrollBar();
        if (hsb != null) {
            hsb.setOpaque(false);
            hsb.setBackground(EdoUi.Internal.TRANSPARENT);
            hsb.setUI(new SubtleScrollBarUI());
        }

        setTransparentCorner(scroll, JScrollPane.UPPER_RIGHT_CORNER);
        setTransparentCorner(scroll, JScrollPane.LOWER_RIGHT_CORNER);
        setTransparentCorner(scroll, JScrollPane.UPPER_LEFT_CORNER);
        setTransparentCorner(scroll, JScrollPane.LOWER_LEFT_CORNER);

        Component view = viewport != null ? viewport.getView() : null;
        if (view instanceof JTable table) {
            table.setOpaque(false);
            table.setBackground(EdoUi.Internal.TRANSPARENT);
        }
    }

    private static void setTransparentCorner(JScrollPane scroll, String cornerKey) {
        JPanel corner = new JPanel();
        corner.setOpaque(false);
        corner.setBackground(EdoUi.Internal.TRANSPARENT);
        scroll.setCorner(cornerKey, corner);
    }

    private static boolean shouldClearWhiteChrome(JComponent jc) {
        if (jc instanceof JButton || jc instanceof JCheckBox) {
            return false;
        }
        if (jc instanceof JLabel label) {
            Border border = label.getBorder();
            if (border instanceof EmptyBorder) {
                label.setOpaque(false);
                label.setBackground(EdoUi.Internal.TRANSPARENT);
            }
            return false;
        }
        if (!(jc instanceof JPanel)) {
            return false;
        }
        if (!jc.isOpaque()) {
            return false;
        }
        return jc.getBackground() == null
                || OverlayComponentColorAnalyzer.isWhiteOrNearWhite(jc.getBackground());
    }
}
