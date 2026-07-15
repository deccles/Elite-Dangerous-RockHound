package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JCheckBox;

/** Orange outline checkbox styling for transparent overlay panels. */
public final class OverlayCheckBoxStyle {

    private static final int ICON_SIZE = 14;

    private OverlayCheckBoxStyle() {
    }

    public static void apply(JCheckBox checkBox) {
        if (checkBox == null) {
            return;
        }
        checkBox.setOpaque(false);
        checkBox.setFocusPainted(false);
        checkBox.setBorderPainted(false);
        checkBox.setContentAreaFilled(false);
        checkBox.setBackground(EdoUi.Internal.TRANSPARENT);
        checkBox.setForeground(EdoUi.User.MAIN_TEXT);
        checkBox.setIcon(unselectedIcon());
        checkBox.setSelectedIcon(selectedIcon());
        checkBox.setDisabledIcon(new OverlayCheckBoxIcon(false, true));
        checkBox.setDisabledSelectedIcon(new OverlayCheckBoxIcon(true, true));
    }

    /** Checked box glyph (medium priority). */
    public static Icon selectedIcon() {
        return new OverlayCheckBoxIcon(true, false);
    }

    /** Empty box glyph (disabled / opt-in). */
    public static Icon unselectedIcon() {
        return new OverlayCheckBoxIcon(false, false);
    }

    private static final class OverlayCheckBoxIcon implements Icon {
        private final boolean selected;
        private final boolean disabled;

        OverlayCheckBoxIcon(boolean selected, boolean disabled) {
            this.selected = selected;
            this.disabled = disabled;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color border = disabled ? EdoUi.Internal.MAIN_TEXT_ALPHA_220 : EdoUi.User.MAIN_TEXT;
                int size = ICON_SIZE - 2;
                int arc = 4;
                g2.setColor(border);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x + 1, y + 1, size, size, arc, arc);
                if (selected) {
                    g2.fillRoundRect(x + 4, y + 4, size - 4, size - 4, 3, 3);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
