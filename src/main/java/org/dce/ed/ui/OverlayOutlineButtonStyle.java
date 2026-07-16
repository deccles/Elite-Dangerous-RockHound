package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.BasicStroke;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;

import org.dce.ed.OverlayPreferences;

/**
 * Outline text button styling (same look as Route tab "Copy next destination").
 * Primary/chip ink and borders follow {@link EdoUi.User#MAIN_TEXT} from Preferences.
 */
public final class OverlayOutlineButtonStyle {

    private static final int DEFAULT_ARC = 12;
    private static final String DANGER_DISABLED_TEXT_KEY = "edo.outlineButton.dangerDisabledText";
    private static final String THEME_INK_KEY = "edo.outlineButton.themeInk";
    /** True red when scripts are running — not coral/salmon. */
    private static final Color DANGER_ACTIVE = new Color(220, 38, 38);
    /** Idle / no scripts running. */
    private static final Color DANGER_IDLE = new Color(130, 130, 130);

    private OverlayOutlineButtonStyle() {
    }

    /** Full-size primary action button (e.g. copy strip). */
    public static void applyPrimary(JButton b, Font uiFont) {
        applyTheme(b, uiFont, true, new Insets(8, 18, 8, 18), true);
    }

    /**
     * Destructive action (e.g. kill rogue scripts).
     *
     * @param active {@code true} when something is running (red); {@code false} when idle (gray)
     */
    public static void applyDanger(JButton b, Font uiFont, boolean active) {
        Color ink = active ? DANGER_ACTIVE : DANGER_IDLE;
        applyFixed(b, uiFont, true, new Insets(8, 18, 8, 18), ink);
        if (b != null) {
            b.putClientProperty(THEME_INK_KEY, Boolean.FALSE);
            b.putClientProperty(DANGER_DISABLED_TEXT_KEY, DANGER_IDLE);
            b.setUI((ButtonUI) DangerOutlineButtonUI.createUI(b));
        }
    }

    /** Compact chip (filter tabs, dismiss). */
    public static void applyChip(JButton b, Font uiFont, boolean selected) {
        applyTheme(b, uiFont, selected, new Insets(4, 10, 4, 10), selected);
    }

    private static void applyTheme(JButton b, Font uiFont, boolean bold, Insets padding, boolean strongBorder) {
        if (b == null || uiFont == null) {
            return;
        }
        int size = OverlayPreferences.getUiFontSize();
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(true);
        b.setBackground(EdoUi.Internal.TRANSPARENT);
        b.putClientProperty(THEME_INK_KEY, Boolean.TRUE);
        b.putClientProperty(DANGER_DISABLED_TEXT_KEY, null);
        b.setUI((ButtonUI) ThemeInkButtonUI.createUI(b));
        b.setBorder(BorderFactory.createCompoundBorder(
                new ThemeRoundedLineBorder(strongBorder, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
    }

    private static void applyFixed(JButton b, Font uiFont, boolean bold, Insets padding, Color borderColor) {
        if (b == null || uiFont == null) {
            return;
        }
        int size = OverlayPreferences.getUiFontSize();
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        b.setForeground(borderColor);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(true);
        b.setBackground(EdoUi.Internal.TRANSPARENT);
        b.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(borderColor, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
    }

    /** Border that tracks Preferences main-text color (and muted alpha variant). */
    public static final class ThemeRoundedLineBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final boolean strong;
        private final int thickness;
        private final int arc;

        public ThemeRoundedLineBorder(boolean strong, int thickness, int arc) {
            this.strong = strong;
            this.thickness = Math.max(1, thickness);
            this.arc = Math.max(2, arc);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(strong ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.MAIN_TEXT_ALPHA_220);
                g2.setStroke(new BasicStroke(thickness));
                int inset = thickness / 2;
                g2.drawRoundRect(x + inset, y + inset, width - thickness - 1, height - thickness - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
    }

    public static final class RoundedLineBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Color color;
        private final int thickness;
        private final int arc;

        public RoundedLineBorder(Color color, int thickness, int arc) {
            this.color = color;
            this.thickness = Math.max(1, thickness);
            this.arc = Math.max(2, arc);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                int inset = thickness / 2;
                g2.drawRoundRect(x + inset, y + inset, width - thickness - 1, height - thickness - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
    }

    /** Paints label text with live {@link EdoUi.User#MAIN_TEXT}. */
    private static final class ThemeInkButtonUI extends BasicButtonUI {
        private static final ThemeInkButtonUI INSTANCE = new ThemeInkButtonUI();

        public static ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, java.awt.Rectangle textRect, String text) {
            if (!Boolean.TRUE.equals(b.getClientProperty(THEME_INK_KEY))) {
                super.paintText(g, b, textRect, text);
                return;
            }
            Color previous = b.getForeground();
            try {
                b.setForeground(EdoUi.User.MAIN_TEXT);
                super.paintText(g, b, textRect, text);
            } finally {
                b.setForeground(previous);
            }
        }
    }

    private static final class DangerOutlineButtonUI extends BasicButtonUI {
        private static final DangerOutlineButtonUI INSTANCE = new DangerOutlineButtonUI();

        public static ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, java.awt.Rectangle textRect, String text) {
            if (!b.getModel().isEnabled()) {
                Object disabled = b.getClientProperty(DANGER_DISABLED_TEXT_KEY);
                if (disabled instanceof Color disabledColor) {
                    Color previous = b.getForeground();
                    try {
                        b.setForeground(disabledColor);
                        b.getModel().setEnabled(true);
                        super.paintText(g, b, textRect, text);
                    } finally {
                        b.getModel().setEnabled(false);
                        b.setForeground(previous);
                    }
                    return;
                }
            }
            super.paintText(g, b, textRect, text);
        }
    }
}
