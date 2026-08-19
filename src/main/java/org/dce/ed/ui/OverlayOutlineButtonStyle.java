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
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;

import org.dce.ed.OverlayPreferences;

/**
 * Outline text button styling (same look as Route tab "Copy next destination").
 * Primary/chip ink and borders follow {@link EdoUi.User#MAIN_TEXT} from Preferences.
 * <p>
 * Factories set {@link EdoLookAndFeel} client properties ({@code edo.buttonRole},
 * {@code edo.hitSafe}, {@code edo.buttonCompact}) then install the matching UI — shims until
 * a global {@code ButtonUI} can read those properties safely.
 */
public final class OverlayOutlineButtonStyle {

    private static final int DEFAULT_ARC = 12;
    private static final String DANGER_DISABLED_TEXT_KEY = "edo.outlineButton.dangerDisabledText";
    private static final String THEME_INK_KEY = "edo.outlineButton.themeInk";

    private OverlayOutlineButtonStyle() {
    }

    /** Full-size primary action button (e.g. copy strip). */
    public static void applyPrimary(JButton b, Font uiFont) {
        applyTheme(b, uiFont, true, paddingPrimary(), true);
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_PRIMARY, false, false);
    }

    /**
     * Primary outline button for translucent / layered overlay hosts (Control Panel, etc.).
     * Uses a dedicated UI that paints an opaque plate so Windows hit-testing delivers clicks
     * (alpha-0 pixels remain click-through even after Selective mode clears {@code WS_EX_TRANSPARENT}).
     */
    public static void applyPrimaryHitSafe(JButton b, Font uiFont) {
        applyPrimaryHitSafe(b, uiFont, true);
    }

    /**
     * @param forceThemeInk when false, label color follows {@link JButton#getForeground()}
     *        (e.g. muted “Copy next destination” when there is nothing to copy)
     */
    public static void applyPrimaryHitSafe(JButton b, Font uiFont, boolean forceThemeInk) {
        applyPrimaryHitSafe(b, uiFont, forceThemeInk, paddingPrimary(), 1f);
    }

    /**
     * Compact hit-safe primary (~70% font/padding) for dense grids such as Combat commands.
     */
    public static void applyPrimaryHitSafeCompact(JButton b, Font uiFont) {
        applyPrimaryHitSafe(b, uiFont, true, paddingCompact(), 0.7f);
    }

    private static void applyPrimaryHitSafe(
            JButton b, Font uiFont, boolean forceThemeInk, Insets padding, float fontScale) {
        if (b == null || uiFont == null) {
            return;
        }
        float scale = fontScale > 0f ? fontScale : 1f;
        int size = Math.max(9, Math.round(OverlayPreferences.getUiFontSize() * scale));
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(Font.BOLD, size));
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorderPainted(true);
        b.putClientProperty(THEME_INK_KEY, forceThemeInk ? Boolean.TRUE : null);
        b.putClientProperty(DANGER_DISABLED_TEXT_KEY, null);
        b.setBorder(BorderFactory.createCompoundBorder(
                new ThemeRoundedLineBorder(true, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_PRIMARY, true, isCompactPadding(padding));
        applyOverlayHitPlate(b);
        b.setUI(HitSafeButtonUI.INSTANCE);
    }

    /**
     * Destructive outline button that stays clickable on translucent overlay hosts.
     *
     * @param active {@code true} when something is running (red); {@code false} when idle (gray)
     */
    public static void applyDangerHitSafe(JButton b, Font uiFont, boolean active) {
        if (b == null || uiFont == null) {
            return;
        }
        Color ink = active ? EdoUi.Internal.OUTLINE_DANGER_ACTIVE : EdoUi.Internal.OUTLINE_IDLE;
        int size = OverlayPreferences.getUiFontSize();
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(Font.BOLD, size));
        b.setForeground(ink);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorderPainted(true);
        b.putClientProperty(THEME_INK_KEY, Boolean.FALSE);
        b.putClientProperty(DANGER_DISABLED_TEXT_KEY, EdoUi.Internal.OUTLINE_IDLE);
        Insets padding = paddingPrimary();
        b.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(ink, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_DANGER, true, false);
        applyOverlayHitPlate(b);
        b.setUI(HitSafeButtonUI.INSTANCE);
    }

    /**
     * Opaque fill so layered windows deliver clicks. Uses fully-opaque RGB (alpha 255) —
     * translucent plates still lose hits on Win32 per-pixel layered windows.
     */
    public static void applyOverlayHitPlate(AbstractButton b) {
        if (b == null) {
            return;
        }
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        if (OverlayPreferences.overlayChromeRequestsTransparency(b) || EdoSurface.isOverlay(b)) {
            b.setBackground(EdoUi.Internal.HIT_PLATE_BG);
        } else {
            Color bg = EdoUi.User.BACKGROUND;
            b.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
        }
    }

    /**
     * Always paints an opaque rounded plate before border/text so clicks land on Control Panel
     * buttons in hybrid / transparent overlay modes.
     */
    private static final class HitSafeButtonUI extends BasicButtonUI {
        static final HitSafeButtonUI INSTANCE = new HitSafeButtonUI();

        @Override
        public void update(Graphics g, JComponent c) {
            if (c instanceof AbstractButton b && b.isOpaque()) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Full opaque fill first — never AlphaComposite.Clear. Cleared corner pixels
                    // on layered/transparent hosts show up as white/bright specks (Combat tab).
                    g2.setColor(b.getBackground());
                    g2.fillRect(0, 0, b.getWidth(), b.getHeight());
                    g2.fillRoundRect(0, 0, b.getWidth(), b.getHeight(), DEFAULT_ARC, DEFAULT_ARC);
                } finally {
                    g2.dispose();
                }
            }
            paint(g, c);
        }

        @Override
        protected void paintButtonPressed(Graphics g, AbstractButton b) {
            // Pressed shading would reintroduce rectangular/light artifacts on the plate.
        }

        @Override
        protected void paintText(Graphics g, AbstractButton b, java.awt.Rectangle textRect, String text) {
            if (Boolean.TRUE.equals(b.getClientProperty(THEME_INK_KEY))) {
                // BasicButtonUI's disabled path derives ink from background.brighter()/darker(),
                // which is nearly invisible on our dark hit-safe plates (Combat fighter orders).
                // Disabled look matches Kill scripts idle: solid gray outline + text.
                Color previous = b.getForeground();
                boolean enabled = b.isEnabled();
                try {
                    b.setForeground(enabled ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.OUTLINE_IDLE);
                    if (!enabled) {
                        b.getModel().setEnabled(true);
                    }
                    super.paintText(g, b, textRect, text);
                } finally {
                    if (!enabled) {
                        b.getModel().setEnabled(false);
                    }
                    b.setForeground(previous);
                }
                return;
            }
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

    /**
     * Destructive action (e.g. kill rogue scripts).
     *
     * @param active {@code true} when something is running (red); {@code false} when idle (gray)
     */
    public static void applyDanger(JButton b, Font uiFont, boolean active) {
        Color ink = active ? EdoUi.Internal.OUTLINE_DANGER_ACTIVE : EdoUi.Internal.OUTLINE_IDLE;
        applyFixed(b, uiFont, true, paddingPrimary(), ink);
        if (b != null) {
            b.putClientProperty(THEME_INK_KEY, Boolean.FALSE);
            b.putClientProperty(DANGER_DISABLED_TEXT_KEY, EdoUi.Internal.OUTLINE_IDLE);
            tagRole(b, EdoLookAndFeel.BUTTON_ROLE_DANGER, false, false);
            b.setUI((ButtonUI) DangerOutlineButtonUI.createUI(b));
        }
    }

    /** Successful completed action, using the standard green success ink. */
    public static void applySuccess(JButton b, Font uiFont) {
        applyFixed(b, uiFont, true, paddingChip(), EdoUi.User.SUCCESS);
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_PRIMARY, false, false);
    }

    /** Compact chip (filter tabs, dismiss). Accepts {@link JToggleButton} for Table/Scatter-style toggles. */
    public static void applyChip(AbstractButton b, Font uiFont, boolean selected) {
        applyTheme(b, uiFont, selected, paddingChip(), selected);
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_CHIP, false, false);
    }

    /**
     * Chip with opaque hit plate for translucent / layered overlay hosts (e.g. Mining Table/Scatter).
     */
    public static void applyChipHitSafe(AbstractButton b, Font uiFont, boolean selected) {
        if (b == null || uiFont == null) {
            return;
        }
        Insets padding = paddingChip();
        int size = OverlayPreferences.getUiFontSize();
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(selected ? Font.BOLD : Font.PLAIN, size));
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorderPainted(true);
        b.putClientProperty(THEME_INK_KEY, Boolean.TRUE);
        b.putClientProperty(DANGER_DISABLED_TEXT_KEY, null);
        b.setBorder(BorderFactory.createCompoundBorder(
                new ThemeRoundedLineBorder(selected, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
        tagRole(b, EdoLookAndFeel.BUTTON_ROLE_CHIP, true, false);
        applyOverlayHitPlate(b);
        b.setUI(HitSafeButtonUI.INSTANCE);
    }

    private static void tagRole(AbstractButton b, String role, boolean hitSafe, boolean compact) {
        if (b == null) {
            return;
        }
        b.putClientProperty(EdoLookAndFeel.BUTTON_ROLE_KEY, role);
        b.putClientProperty(EdoLookAndFeel.HIT_SAFE_KEY, hitSafe ? Boolean.TRUE : null);
        b.putClientProperty(EdoLookAndFeel.BUTTON_COMPACT_KEY, compact ? Boolean.TRUE : null);
    }

    private static boolean isCompactPadding(Insets padding) {
        Insets compact = paddingCompact();
        return padding != null
                && padding.top == compact.top
                && padding.left == compact.left
                && padding.bottom == compact.bottom
                && padding.right == compact.right;
    }

    private static Insets paddingPrimary() {
        Object v = UIManager.get("edo.outlineButton.padding");
        return v instanceof Insets i ? i : new Insets(8, 18, 8, 18);
    }

    private static Insets paddingCompact() {
        Object v = UIManager.get("edo.outlineButton.compactPadding");
        return v instanceof Insets i ? i : new Insets(5, 12, 5, 12);
    }

    private static Insets paddingChip() {
        Object v = UIManager.get("edo.outlineButton.chipPadding");
        return v instanceof Insets i ? i : new Insets(4, 10, 4, 10);
    }

    private static void applyTheme(AbstractButton b, Font uiFont, boolean bold, Insets padding, boolean strongBorder) {
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

    private static void applyFixed(AbstractButton b, Font uiFont, boolean bold, Insets padding, Color borderColor) {
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
                Color ink;
                if (c != null && !c.isEnabled()) {
                    // Match Kill scripts idle gray for disabled primary outline buttons.
                    ink = EdoUi.Internal.OUTLINE_IDLE;
                } else {
                    ink = strong ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.MAIN_TEXT_ALPHA_220;
                }
                g2.setColor(ink);
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
            // BasicButtonUI's disabled path uses background.brighter()/darker(). Chip buttons are
            // transparent, so that paints invisible black text (empty orange outline chips).
            Color previous = b.getForeground();
            boolean enabled = b.isEnabled();
            try {
                b.setForeground(enabled
                        ? EdoUi.User.MAIN_TEXT
                        : EdoUi.Internal.MAIN_TEXT_ALPHA_140);
                if (!enabled) {
                    b.getModel().setEnabled(true);
                }
                super.paintText(g, b, textRect, text);
            } finally {
                if (!enabled) {
                    b.getModel().setEnabled(false);
                }
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
