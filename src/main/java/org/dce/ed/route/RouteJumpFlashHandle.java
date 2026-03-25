package org.dce.ed.route;

/**
 * Swing-owned pending-jump blink timer; route domain calls this interface without depending on {@code javax.swing.Timer}.
 */
public interface RouteJumpFlashHandle {
    boolean isTimerRunning();

    void startTimer();

    void stopTimer();
}
