package org.dce.ed.util;

/** Decides whether overlay windows should remain in Windows' topmost band. */
public final class OverlayForegroundPolicy {

    private OverlayForegroundPolicy() {
    }

    public static boolean keepOverlayTopmost(boolean eliteForeground, boolean rockHoundForeground) {
        return eliteForeground || rockHoundForeground;
    }
}
