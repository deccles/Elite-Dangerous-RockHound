package org.dce.ed;

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
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.dce.ed.ui.EdoUi;

/**
 * Custom "title bar" for the undecorated overlay frame.
 * - Shows a title
 * - Lets you drag the window
 * - Provides a custom-painted close button (red box with X)
 * - Provides a custom-painted gear button (opens Preferences)
 */
public class TitleBarPanel extends JPanel {
    public static final int TOP_RESIZE_STRIP = 9;

    private final OverlayFrame frame;
    private Point dragOffset;
    private final CloseButton closeButton;
    private final SettingsButton settingsButton;
    private final JLabel titleLabel;
    private final JLabel leftStatusLabel;
    private final JLabel rightStatusLabel;

    public TitleBarPanel(OverlayFrame frame, String title) {
        this.frame = frame;

        titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
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

        settingsButton = new SettingsButton();
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    PreferencesDialog dialog = new PreferencesDialog(frame, EliteDangerousOverlay.clientKey);
                    dialog.setLocationRelativeTo(frame);
                    dialog.setVisible(true);
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

        leftStatusLabel = new JLabel("");
        leftStatusLabel.setForeground(Color.RED);
        leftStatusLabel.setFont(leftStatusLabel.getFont().deriveFont(Font.BOLD, 13f));
        leftStatusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        leftStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        leftStatusLabel.setVisible(false);

        rightStatusLabel = new JLabel("");
        rightStatusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        rightStatusLabel.setFont(rightStatusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        rightStatusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        rightStatusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        rightPanel.setOpaque(false);
        rightPanel.add(settingsButton);
        rightPanel.add(closeButton);

        add(leftPanel, BorderLayout.WEST);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(leftStatusLabel, BorderLayout.WEST);
        centerPanel.add(rightStatusLabel, BorderLayout.EAST);

        // Put status in the center so it can expand.
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Tall enough that nothing gets clipped even with DPI scaling
        setPreferredSize(new Dimension(100, 32));

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
                if (child instanceof CloseButton || child instanceof SettingsButton) {
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

    public void setLeftStatusText(String text) {
        if (text == null) {
            text = "";
        }

        final String finalText = text;
        SwingUtilities.invokeLater(() -> {
            leftStatusLabel.setText(finalText);
            leftStatusLabel.setVisible(!finalText.isBlank());
        });
    }

    /**
     * Hide/show the title bar controls when pass-through mode changes.
     * When pass-through is enabled, both the close and gear icons are hidden.
     */
    public void setPassThrough(boolean passThrough) {
        closeButton.setVisible(!passThrough);
        settingsButton.setVisible(!passThrough);
        revalidate();
        repaint();
    }

    /**
     * Simple custom close button: red box with a white X.
     * Drawn with vector shapes.
     */
    private static class CloseButton extends JPanel {

        private boolean hover = false;

        CloseButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setHover(boolean hover) {
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

                Color base = EdoUi.Internal.CLOSE_BG_HOVER;
                Color hoverColor = EdoUi.Internal.CLOSE_BG;
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
     * Simple custom settings button: dark box with a gear-ish shape.
     * Drawn with vector shapes.
     */
    private static class SettingsButton extends JPanel {

        private boolean hover = false;

        SettingsButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setHover(boolean hover) {
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

                // Draw a simple gear: small circle + 6 spokes
                int rOuter = 7;
                int rInner = 3;

                for (int i = 0; i < 6; i++) {
                    double ang = i * (Math.PI / 3.0);
                    int x1 = cx + (int) (Math.cos(ang) * (rInner + 1));
                    int y1 = cy + (int) (Math.sin(ang) * (rInner + 1));
                    int x2 = cx + (int) (Math.cos(ang) * (rOuter));
                    int y2 = cy + (int) (Math.sin(ang) * (rOuter));
                    g2.setStroke(new java.awt.BasicStroke(2f));
                    g2.drawLine(x1, y1, x2, y2);
                }

                g2.fillOval(cx - rInner, cy - rInner, rInner * 2, rInner * 2);

            } finally {
                g2.dispose();
            }
        }
    }

    public void setRightStatusText(String text) {
        if (text == null) {
            text = "";
        }

        final String finalText = text;
        if (SwingUtilities.isEventDispatchThread()) {
            rightStatusLabel.setText(finalText);
        } else {
            SwingUtilities.invokeLater(() -> rightStatusLabel.setText(finalText));
        }
    }
}