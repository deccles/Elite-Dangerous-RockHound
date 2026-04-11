package org.dce.ed.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Status strip region that clips long HTML status text and scrolls it horizontally when it exceeds the
 * viewport (Swing {@link JScrollPane}; avoids null-layout sizing issues with {@link JLabel} in {@link javax.swing.JMenuBar}).
 */
public final class MarqueeStatusScrollPane extends JScrollPane {

    private static final int ROW_H = 32;
    private static final int TIMER_MS = 45;
    private static final int STEP_PX = 2;
    private static final int LOOP_GAP_PX = 40;

    private final JLabel label;
    private final Timer timer;

    private int scrollPos;
    private int textW;
    private int viewW;

    public MarqueeStatusScrollPane(JLabel label) {
        super(label, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.label = label;
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
        getViewport().setOpaque(false);
        setWheelScrollingEnabled(false);

        timer = new Timer(TIMER_MS, e -> tick());

        getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                sync();
            }
        });

        PropertyChangeListener relayout = e -> {
            scrollPos = 0;
            revalidateChain();
            SwingUtilities.invokeLater(MarqueeStatusScrollPane.this::sync);
        };
        label.addPropertyChangeListener("text", relayout);
        label.addPropertyChangeListener("font", relayout);
        label.addPropertyChangeListener("visible", relayout);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(MarqueeStatusScrollPane.this::sync);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }

    private void revalidateChain() {
        revalidate();
        if (getParent() != null) {
            getParent().revalidate();
        }
    }

    private boolean statusActive() {
        String t = label.getText();
        return t != null && !t.isBlank() && label.isVisible();
    }

    @Override
    public Dimension getPreferredSize() {
        if (!statusActive()) {
            return new Dimension(0, ROW_H);
        }
        return new Dimension(240, ROW_H);
    }

    @Override
    public Dimension getMinimumSize() {
        if (!statusActive()) {
            return new Dimension(0, ROW_H);
        }
        return new Dimension(60, ROW_H);
    }

    @Override
    public Dimension getMaximumSize() {
        if (!statusActive()) {
            return new Dimension(0, ROW_H);
        }
        return new Dimension(Integer.MAX_VALUE, ROW_H);
    }

    private void sync() {
        if (!statusActive()) {
            stopTimer();
            label.setPreferredSize(null);
            label.setMinimumSize(null);
            label.setMaximumSize(null);
            try {
                getViewport().setViewPosition(new Point(0, 0));
            } catch (Exception ignored) {
            }
            return;
        }

        label.invalidate();
        label.setMinimumSize(null);
        label.setMaximumSize(null);
        label.setPreferredSize(null);
        Dimension pref = label.getPreferredSize();
        textW = Math.max(1, pref.width);
        int textH = Math.max(ROW_H, pref.height);

        viewW = Math.max(0, getViewport().getWidth());

        if (viewW <= 1) {
            label.setPreferredSize(new Dimension(textW, textH));
            revalidate();
            getViewport().setViewPosition(new Point(0, 0));
            stopTimer();
            return;
        }

        if (textW <= viewW) {
            scrollPos = 0;
            label.setPreferredSize(null);
            label.setMinimumSize(null);
            label.setMaximumSize(null);
            getViewport().setViewPosition(new Point(0, 0));
            stopTimer();
            revalidate();
        } else {
            label.setPreferredSize(new Dimension(textW, textH));
            label.setMinimumSize(new Dimension(textW, textH));
            label.setMaximumSize(new Dimension(textW, textH));
            revalidate();
            viewW = Math.max(1, getViewport().getWidth());
            int maxScroll = textW - viewW + LOOP_GAP_PX;
            if (scrollPos > maxScroll) {
                scrollPos = 0;
            }
            getViewport().setViewPosition(new Point(scrollPos, 0));
            startTimer();
        }
    }

    private void tick() {
        viewW = Math.max(0, getViewport().getWidth());
        if (!isDisplayable() || !statusActive() || textW <= viewW || viewW <= 1) {
            return;
        }
        scrollPos += STEP_PX;
        int maxScroll = textW - viewW + LOOP_GAP_PX;
        if (scrollPos > maxScroll) {
            scrollPos = 0;
        }
        getViewport().setViewPosition(new Point(scrollPos, 0));
    }

    private void startTimer() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void stopTimer() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }

    @Override
    public void removeNotify() {
        stopTimer();
        super.removeNotify();
    }
}
