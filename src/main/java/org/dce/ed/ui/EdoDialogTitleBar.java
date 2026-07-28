package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.util.AppIconUtil;

/**
 * Custom title bar matching the main overlay ({@code TitleBarPanel}): dark plate, white title,
 * minimize (_), and X close. For undecorated dialogs such as Preferences.
 */
public final class EdoDialogTitleBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private Point dragOffset;

    public EdoDialogTitleBar(Window window, String title) {
        setOpaque(true);
        setBackground(EdoUi.Internal.TITLEBAR_BG);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(2, 0, 2, 4));

        JLabel iconLabel = new JLabel();
        iconLabel.setBorder(new EmptyBorder(4, 8, 4, 4));
        BufferedImage icon = AppIconUtil.loadPreparedWindowIcon();
        if (icon != null) {
            iconLabel.setIcon(new ImageIcon(scaleIcon(icon, 16)));
        }

        JLabel titleLabel = new JLabel(title != null ? title : "");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setBorder(new EmptyBorder(4, 2, 4, 8));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(iconLabel);
        left.add(titleLabel);
        add(left, BorderLayout.WEST);

        MinimizeButton minimize = new MinimizeButton();
        minimize.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    EdoWindowIconify.iconifyAll();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                minimize.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                minimize.setHover(false);
            }
        });

        CloseButton close = new CloseButton();
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (window instanceof JDialog dialog) {
                    dialog.dispose();
                } else if (window instanceof JFrame frame) {
                    frame.dispose();
                } else if (window != null) {
                    window.dispose();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                close.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                close.setHover(false);
            }
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.setOpaque(false);
        right.add(minimize);
        right.add(close);
        add(right, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || window == null) {
                    return;
                }
                Point onScreen = e.getLocationOnScreen();
                dragOffset = new Point(onScreen.x - window.getX(), onScreen.y - window.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null || window == null) {
                    return;
                }
                Point onScreen = e.getLocationOnScreen();
                window.setLocation(onScreen.x - dragOffset.x, onScreen.y - dragOffset.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset = null;
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
        left.addMouseListener(drag);
        left.addMouseMotionListener(drag);
        titleLabel.addMouseListener(drag);
        titleLabel.addMouseMotionListener(drag);
        iconLabel.addMouseListener(drag);
        iconLabel.addMouseMotionListener(drag);
    }

    public void refreshTheme() {
        setBackground(EdoUi.Internal.TITLEBAR_BG);
        repaint();
    }

    private static BufferedImage scaleIcon(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, size, size, null);
        } finally {
            g2.dispose();
        }
        return out;
    }

    private static final class MinimizeButton extends JPanel {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        MinimizeButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Minimize all EDO windows");
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
                g2.setColor(hover ? EdoUi.Internal.TITLEBAR_BG_ACTIVE : EdoUi.Internal.TITLEBAR_BG_HOVER);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
                g2.setColor(Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke(2f));
                int y = h / 2 + 1;
                int pad = 7;
                g2.drawLine(pad, y, w - pad, y);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class CloseButton extends JPanel {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        CloseButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Close");
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
                g2.setColor(hover ? EdoUi.Internal.TITLEBAR_BG_ACTIVE : EdoUi.Internal.TITLEBAR_BG_HOVER);
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
}
