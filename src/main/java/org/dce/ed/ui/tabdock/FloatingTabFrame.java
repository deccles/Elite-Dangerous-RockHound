package org.dce.ed.ui.tabdock;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.PaintEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.dce.ed.MouseInteractionMode;
import org.dce.ed.OverlayFrame;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.TitleBarPanel;
import org.dce.ed.ui.EdoSurface;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayBackgroundPanel;
import org.dce.ed.ui.PassThroughCursorOverlay;
import org.dce.ed.ui.ScrollableTabBar;
import org.dce.ed.ui.WindowEdgeResizeSupport;
import org.dce.ed.util.AppIconUtil;
import org.dce.ed.util.EliteWindowFocus;
import org.dce.ed.util.WindowsNativeMousePassThrough;

/**
 * Floating window hosting one or more overlay tabs. Each float has its own mouse-interaction mode
 * (default {@link MouseInteractionMode#SELECTIVE} / hybrid), toggled from the title-bar icon.
 * Transparency and Win32 click-through follow that mode the same way as the main overlay.
 */
public final class FloatingTabFrame extends JFrame implements TabDockHost {

    private static final int NATIVE_STYLE_POLL_MS = 8;

    private final String dockId;
    private final OverlayBackgroundPanel backgroundPanel;
    private final ScrollableTabBar scrollableTabBar;
    private final JPanel cardPanel;
    private final CardLayout cardLayout;
    private final JLabel titleLabel;
    private final JPanel titleBar;
    private final TitleBarPanel.PassThroughToggleButton mouseModeButton;
    private final TitleBarPanel.MinimizeButton minimizeButton;
    private final TitleBarPanel.MinimizeAllButton minimizeAllButton;
    private final TitleBarPanel.CloseButton closeButton;
    private final PassThroughCursorOverlay passThroughCursorOverlay;

    private MouseInteractionMode mouseInteractionMode = MouseInteractionMode.SELECTIVE;
    private String selectedCardName;
    private BiPredicate<String, Point> selectiveHitTester;
    private Consumer<FloatingTabFrame> onCloseRequest;
    private Consumer<FloatingTabFrame> onMovedOrResized;
    private Consumer<FloatingTabFrame> onMouseModeChanged;
    /** Applies overlay chrome to tabs currently hosted in this float. */
    private Consumer<ChromeStyle> chromeApplier;

    private Timer nativeStyleTimer;
    private AWTEventListener paintGuard;
    private boolean nativeReapplyScheduled;

    public record ChromeStyle(Color backgroundWithAlpha, boolean treatAsTransparent) {
    }

    public FloatingTabFrame(String dockId) {
        super("EDO Tabs");
        this.dockId = Objects.requireNonNull(dockId, "dockId");
        passThroughCursorOverlay = new PassThroughCursorOverlay(this);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setAlwaysOnTop(EliteWindowFocus.isEliteForeground());
        // Required for per-pixel alpha (same as OverlayFrame).
        setBackground(new Color(0, 0, 0, 0));
        java.awt.image.BufferedImage icon = AppIconUtil.loadPreparedWindowIcon();
        if (icon != null) {
            setIconImage(icon);
        }

        backgroundPanel = new OverlayBackgroundPanel();
        backgroundPanel.setOpaque(false);
        backgroundPanel.setBackground(new Color(0, 0, 0, 0));
        backgroundPanel.setLayout(new BorderLayout());
        EdoSurface.markOverlay(backgroundPanel);
        setContentPane(backgroundPanel);
        getRootPane().setOpaque(false);

        scrollableTabBar = new ScrollableTabBar(this::isFullPassThrough, false);
        EdoSurface.markOverlay(scrollableTabBar);
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout) {
            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        cardPanel.setOpaque(false);
        cardPanel.setBackground(new Color(0, 0, 0, 0));
        EdoSurface.markOverlay(cardPanel);
        cardPanel.setPreferredSize(new Dimension(420, 640));

        titleLabel = new JLabel("Detached tabs");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        mouseModeButton = new TitleBarPanel.PassThroughToggleButton();
        mouseModeButton.setMouseInteractionMode(mouseInteractionMode);
        mouseModeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    cycleMouseInteractionMode();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                mouseModeButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseModeButton.setHover(false);
            }
        });

        closeButton = new TitleBarPanel.CloseButton();
        closeButton.setToolTipText("Return tabs to the main overlay");
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    requestClose();
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

        minimizeButton = new TitleBarPanel.MinimizeButton();
        minimizeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    org.dce.ed.ui.EdoWindowIconify.iconifyOne(FloatingTabFrame.this);
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

        minimizeAllButton = new TitleBarPanel.MinimizeAllButton();
        minimizeAllButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    org.dce.ed.ui.EdoWindowIconify.iconifyAll();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                minimizeAllButton.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                minimizeAllButton.setHover(false);
            }
        });

        titleBar = new JPanel(new BorderLayout());
        Color titleBg = EdoUi.Internal.TITLEBAR_BG;
        // Fully opaque plate for Win32 hit-testing on layered float frames.
        titleBar.setBackground(new Color(titleBg.getRed(), titleBg.getGreen(), titleBg.getBlue()));
        titleBar.setOpaque(true);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(titleLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.setOpaque(false);
        right.add(mouseModeButton);
        right.add(minimizeAllButton);
        right.add(minimizeButton);
        right.add(closeButton);
        Dimension chromeMin = right.getPreferredSize();
        right.setMinimumSize(chromeMin);
        right.setPreferredSize(chromeMin);
        titleBar.add(left, BorderLayout.CENTER);
        titleBar.add(right, BorderLayout.EAST);
        installTitleDrag(titleBar);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBackground(new Color(0, 0, 0, 0));
        body.add(scrollableTabBar, BorderLayout.NORTH);
        body.add(cardPanel, BorderLayout.CENTER);

        backgroundPanel.add(titleBar, BorderLayout.NORTH);
        backgroundPanel.add(body, BorderLayout.CENTER);

        setMinimumSize(new Dimension(280, 220));
        setSize(480, 720);
        WindowEdgeResizeSupport.install(this);
        applyOverlayBackgroundFromPreferences(mouseInteractionMode.isPassThroughLike());
        org.dce.ed.ui.EdoWindowIconify.watch(this);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                applyMouseInteractionMode(mouseInteractionMode, false);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                passThroughCursorOverlay.dispose();
                stopNativePassThroughSupport();
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                reapplyNativeMousePassThroughIfEnabled();
                fireMovedOrResized();
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                reapplyNativeMousePassThroughIfEnabled();
                fireMovedOrResized();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                applyMouseInteractionMode(mouseInteractionMode, false);
            }

            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                passThroughCursorOverlay.update(mouseInteractionMode, false, null);
            }
        });
        addHierarchyListener(e -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                return;
            }
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                scheduleNativePassThroughReapplyAfterPaint();
            }
        });
    }

    public void setSelectiveHitTester(BiPredicate<String, Point> selectiveHitTester) {
        this.selectiveHitTester = selectiveHitTester;
    }

    public void setChromeApplier(Consumer<ChromeStyle> chromeApplier) {
        this.chromeApplier = chromeApplier;
    }

    public void setOnCloseRequest(Consumer<FloatingTabFrame> onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    public void setOnMovedOrResized(Consumer<FloatingTabFrame> onMovedOrResized) {
        this.onMovedOrResized = onMovedOrResized;
    }

    public void setOnMouseModeChanged(Consumer<FloatingTabFrame> onMouseModeChanged) {
        this.onMouseModeChanged = onMouseModeChanged;
    }

    public void setDockTitle(String title) {
        titleLabel.setText(title == null || title.isBlank() ? "Detached tabs" : title);
        setTitle(titleLabel.getText() + " — EDO");
    }

    public void setSelectedCardName(String cardName) {
        this.selectedCardName = cardName;
    }

    public String getSelectedCardName() {
        return selectedCardName;
    }

    public MouseInteractionMode getMouseInteractionMode() {
        return mouseInteractionMode;
    }

    public void setMouseInteractionMode(MouseInteractionMode mode) {
        applyMouseInteractionMode(mode, true);
    }

    public void cycleMouseInteractionMode() {
        applyMouseInteractionMode(mouseInteractionMode.next(), true);
    }

    private void applyMouseInteractionMode(MouseInteractionMode mode, boolean notify) {
        MouseInteractionMode next = mode != null ? mode : MouseInteractionMode.SELECTIVE;
        this.mouseInteractionMode = next;
        mouseModeButton.setMouseInteractionMode(next);
        applyOverlayBackgroundFromPreferences(next.isPassThroughLike());
        applyPassThrough(next.isPassThroughLike());
        updateNativeStyleTimer();
        if (notify) {
            Consumer<FloatingTabFrame> c = onMouseModeChanged;
            if (c != null) {
                c.accept(this);
            }
        }
        repaint();
    }

    /**
     * Same transparency presets as {@link org.dce.ed.OverlayFrame}: pass-through modes use the
     * pass-through %; Normal uses the normal %.
     */
    private void applyOverlayBackgroundFromPreferences(boolean passThroughMode) {
        int rgb = OverlayPreferences.getUiBackgroundRgb();
        int pct = passThroughMode
                ? OverlayPreferences.getPassThroughTransparencyPercent()
                : OverlayPreferences.getNormalTransparencyPercent();
        Color base = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        Color bg = OverlayPreferences.buildOverlayBackgroundColor(base, pct);
        boolean treatAsTransparent = pct > 0;

        setBackground(new Color(0, 0, 0, 0));
        OverlayPreferences.publishWindowChromeTransparency(getRootPane(), treatAsTransparent, pct);
        getRootPane().putClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY, mouseInteractionMode);
        backgroundPanel.setPaintColor(bg);

        scrollableTabBar.applyOverlayChrome(bg, treatAsTransparent);
        cardPanel.setOpaque(false);
        cardPanel.setBackground(new Color(0, 0, 0, 0));

        Consumer<ChromeStyle> applier = chromeApplier;
        if (applier != null) {
            applier.accept(new ChromeStyle(bg, treatAsTransparent));
        }

        revalidate();
        repaint();
    }

    private boolean isFullPassThrough() {
        return mouseInteractionMode == MouseInteractionMode.FULL_PASS_THROUGH;
    }

    private void applyPassThrough(boolean enable) {
        stampNativeMousePassThrough(enable);
        revalidate();
    }

    private void stampNativeMousePassThrough(boolean enable) {
        if (!isDisplayable() || !WindowsNativeMousePassThrough.isWindows()) {
            return;
        }
        boolean stampEnable = enable && OverlayFrame.shouldStampNativeMousePassThrough(
                mouseInteractionMode, isPointerOverInteractiveChrome());
        WindowsNativeMousePassThrough.applyToWindowTree(this, stampEnable);
    }

    private boolean isPointerOverInteractiveChrome() {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null || !isShowing()) {
            return false;
        }
        Point mouse = pi.getLocation();
        if (containsScreenPoint(titleBar, mouse)) {
            return true;
        }
        if (containsScreenPoint(scrollableTabBar, mouse)) {
            return true;
        }
        // Card-specific controls are interactive only in Selective. FPT keeps all tab content
        // click-through; only the window chrome above remains available as an escape hatch.
        BiPredicate<String, Point> tester = selectiveHitTester;
        String card = selectedCardName;
        if (tester != null && card != null && shouldUseCardSpecificHitRegion(mouseInteractionMode)) {
            return tester.test(card, mouse);
        }
        return false;
    }

    public static boolean shouldUseCardSpecificHitRegion(MouseInteractionMode mode) {
        return mode == MouseInteractionMode.SELECTIVE;
    }

    private static boolean containsScreenPoint(Component component, Point screenPoint) {
        if (component == null || !component.isShowing() || screenPoint == null) {
            return false;
        }
        try {
            Point origin = component.getLocationOnScreen();
            return new Rectangle(origin.x, origin.y, component.getWidth(), component.getHeight())
                    .contains(screenPoint);
        } catch (IllegalComponentStateException ex) {
            return false;
        }
    }

    private void updateNativeStyleTimer() {
        stopNativeStyleTimer();
        if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
            return;
        }
        nativeStyleTimer = new Timer(NATIVE_STYLE_POLL_MS, e -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                stopNativeStyleTimer();
                return;
            }
            stampNativeMousePassThrough(true);
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            Point pointer = pointerInfo != null ? pointerInfo.getLocation() : null;
            boolean inside = pointer != null && containsScreenPoint(this, pointer);
            passThroughCursorOverlay.update(mouseInteractionMode, inside, pointer);
        });
        nativeStyleTimer.setRepeats(true);
        nativeStyleTimer.start();
        installPaintGuard();
    }

    private void stopNativeStyleTimer() {
        if (nativeStyleTimer != null) {
            nativeStyleTimer.stop();
            nativeStyleTimer = null;
        }
        passThroughCursorOverlay.update(mouseInteractionMode, false, null);
    }

    private void installPaintGuard() {
        if (paintGuard != null) {
            return;
        }
        paintGuard = event -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                return;
            }
            if (event.getID() != PaintEvent.PAINT) {
                return;
            }
            if (!(event.getSource() instanceof Component src)) {
                return;
            }
            if (!SwingUtilities.isDescendingFrom(src, this)) {
                return;
            }
            scheduleNativePassThroughReapplyAfterPaint();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(paintGuard, AWTEvent.PAINT_EVENT_MASK);
    }

    private void stopNativePassThroughSupport() {
        stopNativeStyleTimer();
        if (paintGuard != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(paintGuard);
            paintGuard = null;
        }
        if (isDisplayable()) {
            WindowsNativeMousePassThrough.applyToWindowTree(this, false);
        }
    }

    private void reapplyNativeMousePassThroughIfEnabled() {
        if (mouseInteractionMode.isPassThroughLike()) {
            stampNativeMousePassThrough(true);
        }
    }

    private void scheduleNativePassThroughReapplyAfterPaint() {
        if (!mouseInteractionMode.isPassThroughLike() || !isShowing() || nativeReapplyScheduled) {
            return;
        }
        nativeReapplyScheduled = true;
        SwingUtilities.invokeLater(() -> {
            nativeReapplyScheduled = false;
            if (mouseInteractionMode.isPassThroughLike()) {
                stampNativeMousePassThrough(true);
            }
        });
    }

    private void requestClose() {
        stopNativePassThroughSupport();
        Consumer<FloatingTabFrame> c = onCloseRequest;
        if (c != null) {
            c.accept(this);
        } else {
            dispose();
        }
    }

    private void fireMovedOrResized() {
        Consumer<FloatingTabFrame> c = onMovedOrResized;
        if (c != null) {
            c.accept(this);
        }
    }

    private void installTitleDrag(JPanel bar) {
        MouseAdapter drag = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                if (isTitleBarChrome(e.getComponent()) || isOverResizeEdge(e)) {
                    grab = null;
                    return;
                }
                grab = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), bar);
            }

            /** Leave presses on the window's resize border to WindowEdgeResizeSupport. */
            private boolean isOverResizeEdge(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(
                        e.getComponent(), e.getPoint(), getRootPane());
                int edge = 6;
                return p.y < edge || p.x < edge || p.x >= getRootPane().getWidth() - edge;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grab == null) {
                    return;
                }
                Point screen = e.getLocationOnScreen();
                setLocation(screen.x - grab.x, screen.y - grab.y);
            }
        };
        // Attach to every non-button child too: filler panels inside the title bar gain their own
        // mouse listeners (edge-resize support), which stops events from bubbling up to the bar.
        installTitleDragRecursive(bar, drag);
    }

    private void installTitleDragRecursive(Component c, MouseAdapter drag) {
        if (c == null || isTitleBarChrome(c)) {
            return;
        }
        c.addMouseListener(drag);
        c.addMouseMotionListener(drag);
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                installTitleDragRecursive(child, drag);
            }
        }
    }

    private boolean isTitleBarChrome(Component c) {
        if (c == null) {
            return false;
        }
        return c == mouseModeButton || c == minimizeAllButton || c == minimizeButton || c == closeButton
                || SwingUtilities.isDescendingFrom(c, mouseModeButton)
                || SwingUtilities.isDescendingFrom(c, minimizeAllButton)
                || SwingUtilities.isDescendingFrom(c, minimizeButton)
                || SwingUtilities.isDescendingFrom(c, closeButton);
    }

    @Override
    public String getDockId() {
        return dockId;
    }

    @Override
    public Window getWindow() {
        return this;
    }

    @Override
    public JPanel getTabStrip() {
        return scrollableTabBar.getTabStrip();
    }

    @Override
    public JPanel getCardPanel() {
        return cardPanel;
    }

    @Override
    public CardLayout getCardLayout() {
        return cardLayout;
    }

    @Override
    public void onDockTabsChanged() {
        scrollableTabBar.refreshLayout();
        WindowEdgeResizeSupport.install(this);
        // Re-apply this float's chrome to newly hosted tabs.
        applyOverlayBackgroundFromPreferences(mouseInteractionMode.isPassThroughLike());
        revalidate();
        repaint();
    }

    @Override
    public Rectangle getBoundsOnScreen() {
        return getBounds();
    }

    public ScrollableTabBar getScrollableTabBar() {
        return scrollableTabBar;
    }
}
