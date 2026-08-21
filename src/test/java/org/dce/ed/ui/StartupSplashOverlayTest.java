package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.JPanel;
import javax.swing.JRootPane;

import org.junit.jupiter.api.Test;

class StartupSplashOverlayTest {

    @Test
    void restoringHiddenGlassPaneDoesNotCopyVisibleSplashState() {
        JRootPane root = new JRootPane();
        JPanel previous = new JPanel();
        previous.setVisible(false);
        root.setGlassPane(previous);

        JPanel splash = new JPanel();
        root.setGlassPane(splash);
        splash.setVisible(true);

        StartupSplashOverlay.restoreGlassPane(root, previous, false);

        assertSame(previous, root.getGlassPane());
        assertFalse(previous.isVisible(), "dismissed splash must not leave an invisible input shield");
    }
}
