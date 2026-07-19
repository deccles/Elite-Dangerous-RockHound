package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.util.function.BooleanSupplier;

import org.dce.ed.OverlayPreferences;

/**
 * Horizontal tab strip with optional {@code <} / {@code >} scroll when tabs overflow the viewport.
 * In mouse pass-through mode, chevrons scroll on hover; otherwise they scroll on click.
 */
public final class ScrollableTabBar extends JPanel {

    private static final int CHEVRON_HOVER_DELAY_MS = 500;
    private static final int CHEVRON_REPEAT_MS = 350;
    private static final int CHEVRON_WIDTH = 22;

    private final JPanel tabStrip;
    private final JScrollPane scrollPane;
    private final JButton scrollLeftBtn;
    private final JButton scrollRightBtn;
    private final BooleanSupplier passThroughEnabled;

    private final Timer chevronRepeatTimer;

    public ScrollableTabBar(BooleanSupplier passThroughEnabled, boolean opaque) {
        super(new BorderLayout(0, 0));
        this.passThroughEnabled = passThroughEnabled;

        scrollLeftBtn = createChevronButton("<");
        scrollRightBtn = createChevronButton(">");

        tabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));

        scrollPane = new JScrollPane(tabStrip);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

        applyOverlayChrome(opaque ? EdoUi.User.BACKGROUND : EdoUi.Internal.TRANSPARENT, !opaque);

        add(scrollLeftBtn, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);
        add(scrollRightBtn, BorderLayout.EAST);

        scrollLeftBtn.addActionListener(e -> scrollBy(-scrollPageAmount()));
        scrollRightBtn.addActionListener(e -> scrollBy(scrollPageAmount()));

        ChevronHoverPoller.register(scrollLeftBtn, CHEVRON_HOVER_DELAY_MS, () -> scrollBy(-scrollPageAmount()), passThroughEnabled);
        ChevronHoverPoller.register(scrollRightBtn, CHEVRON_HOVER_DELAY_MS, () -> scrollBy(scrollPageAmount()), passThroughEnabled);

        chevronRepeatTimer = new Timer(CHEVRON_REPEAT_MS, e -> {
            if (ChevronHoverPoller.consumeRepeatScrollLeft()) {
                scrollBy(-scrollPageAmount());
            } else if (ChevronHoverPoller.consumeRepeatScrollRight()) {
                scrollBy(scrollPageAmount());
            }
        });
        chevronRepeatTimer.start();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateChevronVisibility();
            }
        });

        tabStrip.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateChevronVisibility();
            }
        });
    }

    public JPanel getTabStrip() {
        return tabStrip;
    }

    /**
     * Match {@link org.dce.ed.EliteOverlayTabbedPane#applyOverlayBackground} for the old flat tab bar.
     * <p>
     * See-through visuals follow the transparency preference in both mouse modes; when mouse
     * pass-through is off, {@link #paintComponent} keeps every pixel at alpha ≥ 1 so Windows layered
     * hit-testing still delivers clicks on tabs and the gaps between them (alpha-0 pixels are
     * click-through to the game).
     */
    public void applyOverlayChrome(Color background, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        Color fill = opaque ? solidHitPlate(background) : background;
        setOpaque(opaque);
        setBackground(fill);
        tabStrip.setOpaque(opaque);
        tabStrip.setBackground(fill);
        scrollPane.setOpaque(opaque);
        scrollPane.setBackground(fill);
        scrollPane.getViewport().setOpaque(opaque);
        scrollPane.getViewport().setBackground(fill);
        scrollLeftBtn.setOpaque(opaque);
        scrollRightBtn.setOpaque(opaque);
        if (opaque) {
            scrollLeftBtn.setBackground(fill);
            scrollRightBtn.setBackground(fill);
        } else {
            scrollLeftBtn.setBackground(EdoUi.Internal.TRANSPARENT);
            scrollRightBtn.setBackground(EdoUi.Internal.TRANSPARENT);
        }
        revalidate();
        repaint();
    }

    private static Color solidHitPlate(Color background) {
        if (background != null && background.getAlpha() > 0) {
            return new Color(background.getRed(), background.getGreen(), background.getBlue());
        }
        Color dark = EdoUi.Internal.DARK_ALPHA_220;
        return new Color(dark.getRed(), dark.getGreen(), dark.getBlue());
    }

    /** Background color with alpha clamped to ≥ 1: visually transparent but still a Win32 click target. */
    static Color minAlphaHitPlate(Color background) {
        Color base = background != null ? background : EdoUi.User.BACKGROUND;
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(1, base.getAlpha()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isOpaque()) {
            boolean mousePassThrough = passThroughEnabled != null && passThroughEnabled.getAsBoolean();
            boolean chromeTransparent = OverlayPreferences.overlayChromeRequestsTransparency();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (chromeTransparent && mousePassThrough) {
                    // Pass-through: fully clear; clicks go to the game anyway.
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } else if (chromeTransparent) {
                    // Interactive overlay: keep the configured transparency but never alpha 0, so
                    // Windows layered hit-testing still delivers clicks on tabs/gaps.
                    g2.setComposite(AlphaComposite.Src);
                    g2.setColor(minAlphaHitPlate(getBackground()));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    // Decorated host / opaque theme: solid plate (Clear corrupts on decorated JFrames).
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(solidHitPlate(getBackground()));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }

    public void refreshLayout() {
        tabStrip.revalidate();
        tabStrip.repaint();
        SwingUtilities.invokeLater(this::updateChevronVisibility);
    }

    private JButton createChevronButton(String label) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 10f));
        b.setMargin(new java.awt.Insets(2, 4, 2, 4));
        b.setPreferredSize(new Dimension(CHEVRON_WIDTH, 24));
        b.setMinimumSize(new Dimension(CHEVRON_WIDTH, 24));
        b.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
        b.setBackground(EdoUi.Internal.DARK_ALPHA_220);
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setVisible(false);
        return b;
    }

    private int scrollPageAmount() {
        int w = scrollPane.getViewport().getExtentSize().width;
        return Math.max(40, w - CHEVRON_WIDTH);
    }

    private void scrollBy(int delta) {
        int x = scrollPane.getHorizontalScrollBar().getValue();
        int max = scrollPane.getHorizontalScrollBar().getMaximum()
                - scrollPane.getHorizontalScrollBar().getVisibleAmount();
        int next = Math.max(0, Math.min(max, x + delta));
        scrollPane.getHorizontalScrollBar().setValue(next);
        updateChevronVisibility();
    }

    private void updateChevronVisibility() {
        if (!isShowing()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            int value = scrollPane.getHorizontalScrollBar().getValue();
            int max = scrollPane.getHorizontalScrollBar().getMaximum()
                    - scrollPane.getHorizontalScrollBar().getVisibleAmount();
            boolean canScroll = max > 0;
            scrollLeftBtn.setVisible(canScroll && value > 0);
            scrollRightBtn.setVisible(canScroll && value < max);
        });
    }

    /**
     * Polls mouse position for chevron hover in pass-through mode (click unreliable).
     */
    private static final class ChevronHoverPoller implements ActionListener {

        private static final int POLL_INTERVAL_MS = 40;
        private static volatile boolean repeatScrollLeft;
        private static volatile boolean repeatScrollRight;

        private static final java.util.List<Entry> entries = new java.util.ArrayList<>();
        private static final Timer pollTimer;

        static {
            pollTimer = new Timer(POLL_INTERVAL_MS, new ChevronHoverPoller());
            pollTimer.start();
        }

        private static class Entry {
            final JButton button;
            final int delayMs;
            final Runnable scrollAction;
            final BooleanSupplier enabled;
            final boolean scrollLeft;

            long hoverStartMs = -1L;

            Entry(JButton button, int delayMs, Runnable scrollAction, BooleanSupplier enabled, boolean scrollLeft) {
                this.button = button;
                this.delayMs = delayMs;
                this.scrollAction = scrollAction;
                this.enabled = enabled;
                this.scrollLeft = scrollLeft;
            }
        }

        static void register(JButton button, int delayMs, Runnable scrollAction, BooleanSupplier enabled) {
            boolean left = "<".equals(button.getText());
            entries.add(new Entry(button, delayMs, scrollAction, enabled, left));
        }

        static boolean consumeRepeatScrollLeft() {
            if (repeatScrollLeft) {
                repeatScrollLeft = false;
                return true;
            }
            return false;
        }

        static boolean consumeRepeatScrollRight() {
            if (repeatScrollRight) {
                repeatScrollRight = false;
                return true;
            }
            return false;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            repeatScrollLeft = false;
            repeatScrollRight = false;

            if (entries.isEmpty()) {
                return;
            }

            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo == null) {
                return;
            }

            Point mouseOnScreen = pointerInfo.getLocation();
            long now = System.currentTimeMillis();

            for (Entry entry : entries) {
                if (entry.enabled == null || !entry.enabled.getAsBoolean()) {
                    entry.hoverStartMs = -1L;
                    continue;
                }

                JButton button = entry.button;
                if (button == null || !button.isShowing() || !button.isVisible()) {
                    entry.hoverStartMs = -1L;
                    continue;
                }

                Point buttonLoc;
                try {
                    buttonLoc = button.getLocationOnScreen();
                } catch (IllegalStateException ex) {
                    entry.hoverStartMs = -1L;
                    continue;
                }

                Rectangle bounds = new Rectangle(
                        buttonLoc.x, buttonLoc.y, button.getWidth(), button.getHeight());

                if (bounds.contains(mouseOnScreen)) {
                    if (entry.hoverStartMs < 0L) {
                        entry.hoverStartMs = now;
                    } else if (now - entry.hoverStartMs >= entry.delayMs) {
                        if (entry.scrollLeft) {
                            repeatScrollLeft = true;
                        } else {
                            repeatScrollRight = true;
                        }
                    }
                } else {
                    entry.hoverStartMs = -1L;
                }
            }
        }
    }
}
