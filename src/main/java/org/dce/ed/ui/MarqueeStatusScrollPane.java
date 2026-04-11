package org.dce.ed.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Clips long HTML status text and scrolls it left in a continuous loop: when the first copy leaves the
 * left edge, the second copy is already entering from the right (no blank pause or jump back to the start).
 */
public final class MarqueeStatusScrollPane extends JScrollPane {

    private static final int ROW_H = 32;
    private static final int TIMER_MS = 45;
    private static final int STEP_PX = 2;
    /** Space between the end of one copy and the start of the duplicate (visible once per loop). */
    private static final int SEGMENT_GAP_PX = 48;

    private final JLabel label;
    private final JLabel ghost;
    private final JPanel track;
    private final Timer timer;

    private boolean overflowMode;
    private int scrollPos;
    private int textW;
    private int viewW;
    private int loopPeriod;

    public MarqueeStatusScrollPane(JLabel label) {
        super(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.label = label;
        this.ghost = new JLabel("");
        ghost.setOpaque(false);

        track = new JPanel();
        track.setLayout(new BoxLayout(track, BoxLayout.X_AXIS));
        track.setOpaque(false);

        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
        getViewport().setOpaque(false);
        setWheelScrollingEnabled(false);
        setViewportView(label);

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
        label.addPropertyChangeListener("foreground", e -> {
            ghost.setForeground(label.getForeground());
        });
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

    /**
     * Duplicate click handling for the trailing copy (e.g. “New version”), after listeners are installed on {@code label}.
     */
    public void addGhostMouseListener(MouseListener listener) {
        ghost.addMouseListener(listener);
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

    private void syncGhostFromPrimary() {
        ghost.setText(label.getText());
        ghost.setFont(label.getFont());
        ghost.setForeground(label.getForeground());
        ghost.setHorizontalAlignment(label.getHorizontalAlignment());
        ghost.setVerticalAlignment(label.getVerticalAlignment());
    }

    private void setOverflowLayout(boolean overflow) {
        if (overflow == overflowMode) {
            return;
        }
        scrollPos = 0;
        if (overflow) {
            track.removeAll();
            track.add(label);
            track.add(Box.createHorizontalStrut(SEGMENT_GAP_PX));
            track.add(ghost);
            setViewportView(track);
        } else {
            track.removeAll();
            setViewportView(label);
        }
        overflowMode = overflow;
    }

    private void clearLabelSizing() {
        label.setPreferredSize(null);
        label.setMinimumSize(null);
        label.setMaximumSize(null);
    }

    private void clearGhostSizing() {
        ghost.setPreferredSize(null);
        ghost.setMinimumSize(null);
        ghost.setMaximumSize(null);
    }

    private void sync() {
        if (!statusActive()) {
            stopTimer();
            overflowMode = false;
            track.removeAll();
            clearLabelSizing();
            clearGhostSizing();
            setViewportView(label);
            try {
                getViewport().setViewPosition(new Point(0, 0));
            } catch (Exception ignored) {
            }
            return;
        }

        label.invalidate();
        clearLabelSizing();
        clearGhostSizing();
        Dimension pref = label.getPreferredSize();
        textW = Math.max(1, pref.width);
        int textH = Math.max(ROW_H, pref.height);

        viewW = Math.max(0, getViewport().getWidth());

        if (viewW <= 1) {
            setOverflowLayout(false);
            label.setPreferredSize(new Dimension(textW, textH));
            revalidate();
            getViewport().setViewPosition(new Point(0, 0));
            stopTimer();
            loopPeriod = 0;
            return;
        }

        if (textW <= viewW) {
            setOverflowLayout(false);
            clearLabelSizing();
            scrollPos = 0;
            getViewport().setViewPosition(new Point(0, 0));
            stopTimer();
            loopPeriod = 0;
            revalidate();
            return;
        }

        setOverflowLayout(true);
        syncGhostFromPrimary();

        label.setPreferredSize(new Dimension(textW, textH));
        label.setMinimumSize(new Dimension(textW, textH));
        label.setMaximumSize(new Dimension(textW, textH));
        ghost.setPreferredSize(new Dimension(textW, textH));
        ghost.setMinimumSize(new Dimension(textW, textH));
        ghost.setMaximumSize(new Dimension(textW, textH));

        loopPeriod = textW + SEGMENT_GAP_PX;
        scrollPos = scrollPos % loopPeriod;

        revalidate();
        viewW = Math.max(1, getViewport().getWidth());
        getViewport().setViewPosition(new Point(scrollPos, 0));
        startTimer();
    }

    private void tick() {
        viewW = Math.max(0, getViewport().getWidth());
        if (!isDisplayable() || !statusActive() || !overflowMode || loopPeriod <= 0 || textW <= viewW || viewW <= 1) {
            return;
        }
        scrollPos += STEP_PX;
        if (scrollPos >= loopPeriod) {
            scrollPos -= loopPeriod;
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
