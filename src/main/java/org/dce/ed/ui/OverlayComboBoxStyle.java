package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * Forces overlay combo colors on Windows L&amp;F (otherwise closed combos keep system gray/white).
 */
public final class OverlayComboBoxStyle {

    private OverlayComboBoxStyle() {
    }

    public static void apply(JComboBox<?> combo, Font font) {
        if (combo == null) {
            return;
        }
        if (font != null) {
            combo.setFont(font);
        }
        combo.setForeground(EdoUi.User.MAIN_TEXT);
        combo.setBackground(EdoUi.User.PANEL_BG);
        combo.setOpaque(true);
        combo.setFocusable(false);
        combo.setUI(new OverlayComboBoxUI());
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140, 1),
                new EmptyBorder(2, 6, 2, 4)));
    }

    /** Re-apply ink after enable/disable or theme changes. */
    public static void refreshInk(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        combo.setForeground(EdoUi.User.MAIN_TEXT);
        combo.setBackground(EdoUi.User.PANEL_BG);
        combo.repaint();
    }

    private static final class OverlayComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            Color bg = EdoUi.User.PANEL_BG;
            Color ink = EdoUi.User.MAIN_TEXT;
            JButton button = new BasicArrowButton(BasicArrowButton.SOUTH, bg, ink, ink, bg) {
                @Override
                public void setBackground(Color color) {
                    super.setBackground(bg);
                }
            };
            button.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
            button.setOpaque(true);
            button.setBackground(bg);
            button.setForeground(ink);
            button.setMargin(new Insets(0, 0, 0, 0));
            return button;
        }

        @Override
        public void paint(java.awt.Graphics g, javax.swing.JComponent c) {
            // Always theme-fill first — disabled Windows L&F otherwise paints a white plate,
            // which is especially visible when the closed combo has no selection.
            g.setColor(EdoUi.User.PANEL_BG);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            super.paint(g, c);
        }

        @Override
        public void paintCurrentValueBackground(java.awt.Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
            g.setColor(EdoUi.User.PANEL_BG);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paintCurrentValue(java.awt.Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
            // BasicComboBoxUI forces ComboBox.disabledBackground (often white) when disabled —
            // keep theme colors for empty/disabled closed combos (e.g. Unengineered Experimental).
            @SuppressWarnings("unchecked")
            javax.swing.ListCellRenderer<Object> renderer =
                    (javax.swing.ListCellRenderer<Object>) comboBox.getRenderer();
            if (renderer == null) {
                return;
            }
            boolean selected = hasFocus && !isPopupVisible(comboBox);
            Component c = renderer.getListCellRendererComponent(
                    listBox, comboBox.getSelectedItem(), -1, selected, false);
            c.setFont(comboBox.getFont());
            c.setForeground(EdoUi.User.MAIN_TEXT);
            c.setBackground(selected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
            if (c instanceof javax.swing.JComponent jc) {
                jc.setOpaque(true);
            }
            int x = bounds.x;
            int y = bounds.y;
            int w = bounds.width;
            int h = bounds.height;
            if (padding != null) {
                x += padding.left;
                y += padding.top;
                w -= padding.left + padding.right;
                h -= padding.top + padding.bottom;
            }
            boolean shouldValidate = c instanceof javax.swing.JPanel;
            currentValuePane.paintComponent(g, c, comboBox, x, y, w, h, shouldValidate);
        }
    }
}
