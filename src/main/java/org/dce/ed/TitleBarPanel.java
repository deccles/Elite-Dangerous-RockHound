package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ImageIcon;
import javax.swing.border.EmptyBorder;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.util.AppIconUtil;

/**
 * Custom "title bar" for the undecorated overlay frame.
 * - Shows a title
 * - Lets you drag the window
 * - Provides a custom-painted close button (red box with X)
 * - Provides hammer (Tools) and gear (Preferences); pass-through uses hover-dwell from {@link OverlayFrame}.
 */
public class TitleBarPanel extends JPanel {
    public static final int TOP_RESIZE_STRIP = 9;

    public static final String TOOLTIP_HAMMER = "Tools — updates, journal monitor, debug utilities, console…";
    public static final String TOOLTIP_GEAR = "Preferences — theme, overlay transparency, hotkeys…";
    public static final String TOOLTIP_CLOSE = "Close RockHound";
    public static final String TOOLTIP_MINIMIZE = "Minimize all EDO windows";
    public static final String TOOLTIP_PASS_THROUGH_ON =
            "Full pass-through — clicks go to the game; hover here to use title-bar buttons (click to cycle mode)";
    public static final String TOOLTIP_PASS_THROUGH_SELECTIVE =
            "Selective — chrome and listed controls take clicks; other overlay areas pass through (click to cycle mode)";
    public static final String TOOLTIP_PASS_THROUGH_OFF =
            "Normal — click the overlay normally (click to cycle: Selective, then Full pass-through)";
    /** Normal window: click to open the undecorated pass-through overlay. */
    public static final String TOOLTIP_PASS_THROUGH_SWITCH_TO_PT =
            "Pass-through mode — switch to the transparent overlay (clicks go to the game; dwell on title controls)";

    private final OverlayFrame frame;
    private Point dragOffset;
    private final CloseButton closeButton;
    private final MinimizeButton minimizeButton;
    private final TitleBarPanel.PassThroughToggleButton passThroughToggleButton;
    private final SettingsButton settingsButton;
    private final HammerButton hammerButton;
    private final JLabel titleLabel;

    public TitleBarPanel(OverlayFrame frame, String title, javax.swing.JMenu toolsMenu) {
        this.frame = frame;

        JLabel iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(4, 8, 4, 4));
        java.awt.image.BufferedImage icon = AppIconUtil.loadPreparedWindowIcon();
        if (icon != null) {
            iconLabel.setIcon(new ImageIcon(scaleTitleBarIcon(icon, 16)));
        }

        titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setBorder(new EmptyBorder(4, 2, 4, 8));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(iconLabel);
        leftPanel.add(this.titleLabel);

        setOpaque(true);
        setBackground(EdoUi.Internal.TITLEBAR_BG);
        setLayout(new BorderLayout());

        closeButton = new CloseButton();
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    frame.closeOverlay();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setHover(false);
            }
        });

        minimizeButton = new MinimizeButton();
        minimizeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    org.dce.ed.ui.EdoWindowIconify.iconifyAll();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                minimizeButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                minimizeButton.setHover(false);
            }
        });

        hammerButton = new HammerButton();
        hammerButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    OverlayMenuStatusBar.showToolsPopup(hammerButton, toolsMenu);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hammerButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hammerButton.setHover(false);
            }
        });

        settingsButton = new SettingsButton();
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    PreferencesDialog.show(frame, EliteDangerousOverlay.clientKey);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                settingsButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                settingsButton.setHover(false);
            }
        });

        passThroughToggleButton = new TitleBarPanel.PassThroughToggleButton();
        passThroughToggleButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    frame.cycleMouseInteractionMode();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                passThroughToggleButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                passThroughToggleButton.setHover(false);
            }
        });

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        rightPanel.setOpaque(false);
        rightPanel.add(hammerButton);
        rightPanel.add(settingsButton);
        rightPanel.add(passThroughToggleButton);
        rightPanel.add(minimizeButton);
        rightPanel.add(closeButton);
        // Keep chrome controls from being crushed when the overlay is very narrow.
        Dimension chromeMin = rightPanel.getPreferredSize();
        rightPanel.setMinimumSize(chromeMin);
        rightPanel.setPreferredSize(chromeMin);

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        // Tall enough for the larger mouse-mode plate + padding under DPI scaling
        setPreferredSize(new Dimension(100, 36));
        setMinimumSize(new Dimension(160, 36));

        // Drag-to-move behavior
        MouseAdapter dragListener = new MouseAdapter() {

            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = toTitleBarPoint(e);
                if (p.y <= TOP_RESIZE_STRIP) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                } else {
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                // IMPORTANT: event coordinates are relative to the event source (which might be a child).
                // Convert to TitleBarPanel coordinates so our resize-strip test matches what the user sees.
                Point p = toTitleBarPoint(e);

                // If we’re in the very top strip, this is a “resize zone” – let ResizeHandler handle it.
                if (p.y <= TOP_RESIZE_STRIP) {
                    dragOffset = null;
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }

                // Normal title-bar drag: use screen coords relative to frame origin
                java.awt.Point screen = e.getLocationOnScreen();
                dragOffset = new Point(screen.x - frame.getX(), screen.y - frame.getY());
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) {
                    return;
                }

                int newX = e.getXOnScreen() - dragOffset.x;
                int newY = e.getYOnScreen() - dragOffset.y;
                frame.setLocation(newX, newY);
            }

            private Point toTitleBarPoint(MouseEvent e) {
                Component src = (Component) e.getSource();
                return SwingUtilities.convertPoint(src, e.getPoint(), TitleBarPanel.this);
            }
        };

        // NOTE: Swing mouse events do NOT bubble to parents. Most of the title bar is covered by child
        // components (labels/panels), so attach to those children as well. Do NOT attach to the buttons.
        installDragListener(this, dragListener);
    }

    private void installDragListener(Component c, MouseAdapter dragListener) {
        c.addMouseListener(dragListener);
        c.addMouseMotionListener(dragListener);

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (child instanceof CloseButton || child instanceof MinimizeButton
                        || child instanceof SettingsButton || child instanceof HammerButton
                        || child instanceof TitleBarPanel.PassThroughToggleButton) {
                    continue;
                }
                installDragListener(child, dragListener);
            }
        }
    }

    public void setTitleText(String text) {
        if (text == null) {
            text = "";
        }

        final String finalText = text;
        SwingUtilities.invokeLater(() -> titleLabel.setText(finalText));
    }

    /**
     * Updates title-bar mouse-mode control appearance for the current {@link MouseInteractionMode}.
     */
    public void setMouseInteractionMode(MouseInteractionMode mode) {
        closeButton.setVisible(true);
        minimizeButton.setVisible(true);
        passThroughToggleButton.setVisible(true);
        passThroughToggleButton.setMouseInteractionMode(mode);
        hammerButton.setVisible(true);
        settingsButton.setVisible(true);
        revalidate();
        repaint();
    }

    /** @deprecated Prefer {@link #setMouseInteractionMode(MouseInteractionMode)} */
@Deprecated
    public void setPassThrough(boolean passThrough) {
        setMouseInteractionMode(passThrough
                ? MouseInteractionMode.FULL_PASS_THROUGH
                : MouseInteractionMode.NORMAL);
    }

    /**
     * In pass-through mode, click events are forwarded to the game; OverlayFrame drives hover state
     * programmatically from global mouse position.
     */
    public void setCloseHoverProgrammatic(boolean hover) {
        closeButton.setHover(hover);
    }

    public void setMinimizeHoverProgrammatic(boolean hover) {
        minimizeButton.setHover(hover);
    }

    public void setToggleHoverProgrammatic(boolean hover) {
        passThroughToggleButton.setHover(hover);
    }

    public void setHammerHoverProgrammatic(boolean hover) {
        hammerButton.setHover(hover);
    }

    public void setSettingsHoverProgrammatic(boolean hover) {
        settingsButton.setHover(hover);
    }

    public Rectangle getCloseButtonScreenBounds() {
        if (!isShowing() || !closeButton.isShowing()) {
            return null;
        }
        try {
            Point p = closeButton.getLocationOnScreen();
            return new Rectangle(p.x, p.y, closeButton.getWidth(), closeButton.getHeight());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    public Rectangle getMinimizeButtonScreenBounds() {
        if (!isShowing() || !minimizeButton.isShowing()) {
            return null;
        }
        try {
            Point p = minimizeButton.getLocationOnScreen();
            return new Rectangle(p.x, p.y, minimizeButton.getWidth(), minimizeButton.getHeight());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    public Rectangle getToggleButtonScreenBounds() {
        if (!isShowing() || !passThroughToggleButton.isShowing()) {
            return null;
        }
        try {
            Point p = passThroughToggleButton.getLocationOnScreen();
            // Inflate dwell hit box — 24–30px next to Close is easy to miss with the game crosshair.
            Rectangle r = new Rectangle(p.x, p.y, passThroughToggleButton.getWidth(),
                    passThroughToggleButton.getHeight());
            r.grow(10, 8);
            return r;
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    public Rectangle getHammerButtonScreenBounds() {
        if (!isShowing() || !hammerButton.isShowing()) {
            return null;
        }
        try {
            Point p = hammerButton.getLocationOnScreen();
            return new Rectangle(p.x, p.y, hammerButton.getWidth(), hammerButton.getHeight());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    public Rectangle getSettingsButtonScreenBounds() {
        if (!isShowing() || !settingsButton.isShowing()) {
            return null;
        }
        try {
            Point p = settingsButton.getLocationOnScreen();
            return new Rectangle(p.x, p.y, settingsButton.getWidth(), settingsButton.getHeight());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    public void showToolsMenuUnderHammer(javax.swing.JMenu toolsMenu) {
        OverlayMenuStatusBar.showToolsPopup(hammerButton, toolsMenu);
    }

    /**
     * Mouse-mode cycle control: Normal (pointer), Selective (pointer + strike + white top-half outline rect),
     * Full pass-through (pointer + red strike).
     */
    public static class PassThroughToggleButton extends JPanel {
        private boolean hover = false;
        private MouseInteractionMode mode = MouseInteractionMode.NORMAL;

        public PassThroughToggleButton() {
            setOpaque(false);
            // Larger than other chrome icons — primary escape hatch from pass-through.
            setPreferredSize(new Dimension(30, 30));
            setMinimumSize(new Dimension(30, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            refreshToolTipText();
        }

        void refreshToolTipText() {
            setToolTipText(switch (mode) {
                case SELECTIVE -> TOOLTIP_PASS_THROUGH_SELECTIVE;
                case FULL_PASS_THROUGH -> TOOLTIP_PASS_THROUGH_ON;
                case NORMAL -> TOOLTIP_PASS_THROUGH_OFF;
            });
        }

        /** Tooltip when not toggling (e.g. decorated window “enter pass-through” action). */
        public void setPassThroughToolTipOverride(String text) {
            if (text == null || text.isBlank()) {
                refreshToolTipText();
            } else {
                setToolTipText(text);
            }
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        public void setMouseInteractionMode(MouseInteractionMode mode) {
            this.mode = mode != null ? mode : MouseInteractionMode.NORMAL;
            refreshToolTipText();
            repaint();
        }

        /** @deprecated Prefer {@link #setMouseInteractionMode(MouseInteractionMode)} */
@Deprecated
        public void setPassThroughActive(boolean passThroughActive) {
            setMouseInteractionMode(passThroughActive
                    ? MouseInteractionMode.FULL_PASS_THROUGH
                    : MouseInteractionMode.NORMAL);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                boolean passThroughLike = mode.isPassThroughLike();
                Color base = passThroughLike ? EdoUi.Internal.TITLEBAR_BG_ACTIVE : EdoUi.Internal.TITLEBAR_BG_HOVER;
                Color hoverColor = passThroughLike ? EdoUi.Internal.MENU_ACCENT : EdoUi.Internal.TITLEBAR_BG_ACTIVE;
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                if (passThroughLike) {
                    // Arrow first, then red strike on top so the bar clearly crosses the pointer.
                    g2.setColor(Color.WHITE);
                    drawArrowOutline(g2, 2, w, h);
                    g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                            java.awt.BasicStroke.JOIN_ROUND));
                    g2.setColor(EdoUi.User.ERROR);
                    g2.drawLine(Math.max(2, w / 10), h - 5, w - 4, 4);
                    if (mode == MouseInteractionMode.SELECTIVE) {
                        // Unfilled white rectangle over the top half of the pass-through icon.
                        g2.setStroke(new java.awt.BasicStroke(1.4f));
                        g2.setColor(Color.WHITE);
                        g2.drawRect(3, 2, w - 8, Math.max(4, (h / 2) - 3));
                    }
                } else {
                    g2.setColor(Color.WHITE);
                    drawArrowOutline(g2, 0, w, h);
                }
            } finally {
                g2.dispose();
            }
        }

        /**
         * Outlined pointer arrow (previous iteration’s shape); {@code dx} shifts right with strike-through.
         * Coordinates are authored for a 24×24 plate and scaled to the current button size.
         */
        private static void drawArrowOutline(Graphics2D g2, int dx, int w, int h) {
            java.awt.geom.AffineTransform prev = g2.getTransform();
            double sx = w / 24.0;
            double sy = h / 24.0;
            g2.translate(dx * sx, 0);
            g2.scale(sx, sy);
            try {
                g2.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND));
                GeneralPath arrow = new GeneralPath();
                arrow.moveTo(5f, 5f);
                arrow.lineTo(5f, 17f);
                arrow.lineTo(8f, 14f);
                arrow.lineTo(11f, 19f);
                arrow.lineTo(13f, 18f);
                arrow.lineTo(10f, 13f);
                arrow.lineTo(14f, 13f);
                arrow.closePath();
                g2.draw(arrow);
            } finally {
                g2.setTransform(prev);
            }
        }
    }

    /**
     * Simple custom minimize button: rounded plate with a white underscore.
     */
    public static class MinimizeButton extends JPanel {

        private boolean hover = false;

        public MinimizeButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(TOOLTIP_MINIMIZE);
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                Color base = EdoUi.Internal.TITLEBAR_BG_HOVER;
                Color hoverColor = EdoUi.Internal.TITLEBAR_BG_ACTIVE;
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                g2.setColor(Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke(2f));
                int padX = 7;
                int y = h / 2 + 3;
                g2.drawLine(padX, y, w - padX, y);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Simple custom close button: rounded plate with a white X (same look as the main overlay).
     */
    public static class CloseButton extends JPanel {

        private boolean hover = false;

        public CloseButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(TOOLTIP_CLOSE);
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                Color base = EdoUi.Internal.TITLEBAR_BG_HOVER;
                Color hoverColor = EdoUi.Internal.TITLEBAR_BG_ACTIVE;
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                g2.setColor(Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke(2f));

                int pad = 7;
                g2.drawLine(pad, pad, w - pad, h - pad);
                g2.drawLine(w - pad, pad, pad, h - pad);

            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Hammer icon — opens Tools popup (same items as former Menu → Tools).
     */
    public static class HammerButton extends JPanel {

        private static final String HAMMER_ICON_RESOURCE = "/org/dce/ed/hammer-tools.png";

        private static volatile BufferedImage hammerIconWhite;
        private static volatile boolean hammerIconLoadTried;

        private boolean hover = false;

        HammerButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(TOOLTIP_HAMMER);
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        /**
         * Black (or dark) pixels become opaque white; light background becomes transparent.
         * Preserves soft edges when the source is anti-aliased.
         */
        private static BufferedImage blackSilhouetteToWhiteTransparent(BufferedImage src) {
            int w = src.getWidth();
            int h = src.getHeight();
            BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gx = argb.createGraphics();
            try {
                gx.drawImage(src, 0, 0, null);
            } finally {
                gx.dispose();
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = argb.getRGB(x, y);
                    int aIn = (rgb >>> 24) & 0xff;
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int lum = (r * 30 + g * 59 + b * 11) / 100;
                    int outAlpha = aIn * (255 - lum) / 255;
                    if (outAlpha < 8) {
                        continue;
                    }
                    out.setRGB(x, y, (outAlpha << 24) | 0xffffff);
                }
            }
            return out;
        }

        private static BufferedImage hammerIconForPaint() {
            if (hammerIconLoadTried) {
                return hammerIconWhite;
            }
            synchronized (HammerButton.class) {
                if (hammerIconLoadTried) {
                    return hammerIconWhite;
                }
                hammerIconLoadTried = true;
                try (InputStream in = TitleBarPanel.class.getResourceAsStream(HAMMER_ICON_RESOURCE)) {
                    if (in != null) {
                        BufferedImage raw = ImageIO.read(in);
                        if (raw != null) {
                            hammerIconWhite = blackSilhouetteToWhiteTransparent(raw);
                        }
                    }
                } catch (IOException ignored) {
                    hammerIconWhite = null;
                }
                return hammerIconWhite;
            }
        }

        private static void paintFallbackVectorHammer(Graphics2D g2, int w, int h) {
            g2.setColor(Color.WHITE);
            int cx = w / 2;
            g2.fillRoundRect(4, 5, 15, 5, 2, 2);
            g2.fillRoundRect(cx - 2, 10, 4, 10, 2, 2);
            g2.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.drawLine(17, 5, 19, 8);
            g2.drawLine(17, 10, 19, 7);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                Color base = EdoUi.Internal.TITLEBAR_BG_HOVER;
                Color hoverColor = EdoUi.Internal.TITLEBAR_BG_ACTIVE;
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                BufferedImage hammer = hammerIconForPaint();
                if (hammer != null) {
                    int pad = 3;
                    int avail = Math.min(w, h) - pad * 2;
                    if (avail > 0) {
                        int iw = hammer.getWidth();
                        int ih = hammer.getHeight();
                        double scale = Math.min((double) avail / iw, (double) avail / ih);
                        int dw = Math.max(1, (int) Math.round(iw * scale));
                        int dh = Math.max(1, (int) Math.round(ih * scale));
                        int dx = (w - dw) / 2;
                        int dy = (h - dh) / 2;
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(hammer, dx, dy, dw, dh, null);
                    }
                } else {
                    paintFallbackVectorHammer(g2, w, h);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Simple custom settings button: dark box with a gear-ish shape.
     * Drawn with vector shapes.
     */
    public static class SettingsButton extends JPanel {

        private boolean hover = false;

        SettingsButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(TOOLTIP_GEAR);
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                Color base = EdoUi.Internal.TITLEBAR_BG_HOVER;
                Color hoverColor = EdoUi.Internal.TITLEBAR_BG_ACTIVE;
                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);

                g2.setColor(Color.WHITE);
                int cx = w / 2;
                int cy = h / 2;
                int rInner = 3;
                int rMid = 5;
                int tooth = 7;

                // Gear teeth (8 cardinal/intercardinal spokes).
                g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double ang = i * (Math.PI / 4.0);
                    int x1 = cx + (int) Math.round(Math.cos(ang) * rMid);
                    int y1 = cy + (int) Math.round(Math.sin(ang) * rMid);
                    int x2 = cx + (int) Math.round(Math.cos(ang) * tooth);
                    int y2 = cy + (int) Math.round(Math.sin(ang) * tooth);
                    g2.drawLine(x1, y1, x2, y2);
                }

                // Outer ring + inner hub to read as a gear icon at small sizes.
                g2.setStroke(new java.awt.BasicStroke(1.6f));
                g2.drawOval(cx - rMid, cy - rMid, rMid * 2, rMid * 2);
                g2.fillOval(cx - rInner, cy - rInner, rInner * 2, rInner * 2);

            } finally {
                g2.dispose();
            }
        }
    }

    /** Match taskbar/window icon pipeline: preserve alpha when scaling for the title strip. */
    private static BufferedImage scaleTitleBarIcon(BufferedImage src, int maxSide) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0 || maxSide <= 0) {
            return src;
        }
        double scale = Math.min((double) maxSide / sw, (double) maxSide / sh);
        int tw = Math.max(1, (int) Math.round(sw * scale));
        int th = Math.max(1, (int) Math.round(sh * scale));
        if (tw == sw && th == sh) {
            return src;
        }
        BufferedImage dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g2.dispose();
        }
        return dst;
    }

}
