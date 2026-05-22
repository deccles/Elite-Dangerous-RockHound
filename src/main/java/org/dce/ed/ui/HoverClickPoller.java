package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Poll-based hover-to-click for buttons when Swing mouse events do not reach the overlay (MPT).
 */
public final class HoverClickPoller implements ActionListener {

    private static final int POLL_INTERVAL_MS = 40;

    private static final List<Entry> entries = new ArrayList<>();
    private static final Timer pollTimer;

    static {
        pollTimer = new Timer(POLL_INTERVAL_MS, new HoverClickPoller());
        pollTimer.start();
    }

    private static final class Entry {
        final JButton button;
        final int delayMs;
        final Runnable action;
        final BooleanSupplier enabled;

        long hoverStartMs = -1L;
        boolean firedForCurrentHover;

        Entry(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
            this.button = button;
            this.delayMs = delayMs;
            this.action = action;
            this.enabled = enabled;
        }
    }

    private HoverClickPoller() {
    }

    public static void register(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
        if (button == null || action == null) {
            return;
        }
        entries.add(new Entry(button, delayMs, action, enabled));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (entries.isEmpty()) {
            return;
        }
        java.awt.PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            resetAll();
            return;
        }
        Point mouseOnScreen = pointerInfo.getLocation();
        long now = System.currentTimeMillis();

        for (Entry entry : entries) {
            if (entry.enabled != null && !entry.enabled.getAsBoolean()) {
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            JButton button = entry.button;
            if (button == null || !button.isShowing() || !button.isEnabled()) {
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Point buttonLoc;
            try {
                buttonLoc = button.getLocationOnScreen();
            } catch (IllegalStateException ex) {
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Rectangle bounds = new Rectangle(
                    buttonLoc.x, buttonLoc.y, button.getWidth(), button.getHeight());
            if (bounds.contains(mouseOnScreen)) {
                if (entry.hoverStartMs < 0L) {
                    entry.hoverStartMs = now;
                    entry.firedForCurrentHover = false;
                } else if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.delayMs) {
                    entry.firedForCurrentHover = true;
                    SwingUtilities.invokeLater(entry.action);
                }
            } else {
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
            }
        }
    }

    private static void resetAll() {
        for (Entry entry : entries) {
            entry.hoverStartMs = -1L;
            entry.firedForCurrentHover = false;
        }
    }
}
