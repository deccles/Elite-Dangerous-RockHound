package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class EdoWindowIconifyTest {

    @Test
    void iconifyOneLeavesOtherRockHoundWindowsVisible() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        JFrame[] frames = showTwoFrames();
        try {
            SwingUtilities.invokeAndWait(() -> EdoWindowIconify.iconifyOne(frames[0]));
            assertTrue(isIconified(frames[0]));
            assertFalse(isIconified(frames[1]));
        } finally {
            dispose(frames);
        }
    }

    @Test
    void restoringOneWindowAfterIconifyAllRestoresTheGroup() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        JFrame[] frames = showTwoFrames();
        try {
            SwingUtilities.invokeAndWait(EdoWindowIconify::iconifyAll);
            assertTrue(isIconified(frames[0]));
            assertTrue(isIconified(frames[1]));

            SwingUtilities.invokeAndWait(() -> frames[0].setExtendedState(Frame.NORMAL));
            SwingUtilities.invokeAndWait(() -> { });

            assertFalse(isIconified(frames[0]));
            assertFalse(isIconified(frames[1]));
        } finally {
            dispose(frames);
        }
    }

    private static JFrame[] showTwoFrames() throws Exception {
        JFrame[] frames = { new TestEdoFrame("one"), new TestEdoFrame("two") };
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < frames.length; i++) {
                frames[i].setBounds(-20_000 + i * 220, -20_000, 180, 100);
                frames[i].setVisible(true);
                EdoWindowIconify.watch(frames[i]);
            }
        });
        return frames;
    }

    private static final class TestEdoFrame extends JFrame {
        TestEdoFrame(String title) {
            super(title);
        }
    }

    private static boolean isIconified(JFrame frame) {
        return (frame.getExtendedState() & Frame.ICONIFIED) != 0;
    }

    private static void dispose(JFrame[] frames) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (JFrame frame : frames) frame.dispose();
        });
    }
}
