package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.dce.ed.OverlayContentPanel;
import org.dce.ed.OverlayFrame;
import org.junit.jupiter.api.Test;

class WindowEdgeResizeSupportTest {

    @Test
    void mainOverlayUsesTransferablePerWindowResizeSupport() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        OverlayFrame[] frame = new OverlayFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> frame[0] = new OverlayFrame(new OverlayContentPanel(() -> false)));
            assertTrue(WindowEdgeResizeSupport.isInstalledFor(frame[0]));
        } finally {
            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    @Test
    void reparentedComponentResizesOnlyItsCurrentWindow() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        JFrame first = new JFrame();
        JFrame second = new JFrame();
        JPanel moved = new JPanel();
        try {
            SwingUtilities.invokeAndWait(() -> {
                first.setLayout(new BorderLayout());
                first.add(moved);
                first.setBounds(-20_000, -20_000, 300, 240);
                first.setVisible(true);
                WindowEdgeResizeSupport.install(first);

                first.remove(moved);
                second.setLayout(new BorderLayout());
                second.add(moved);
                second.setBounds(-19_500, -20_000, 300, 240);
                second.setVisible(true);
                WindowEdgeResizeSupport.install(second);
                second.validate();
            });

            Rectangle firstBefore = first.getBounds();
            Rectangle secondBefore = second.getBounds();
            SwingUtilities.invokeAndWait(() -> dragEastEdge(moved, second));

            assertTrue(first.getBounds().equals(firstBefore),
                    "the component must no longer resize its previous window");
            assertTrue(second.getWidth() > secondBefore.width,
                    "the component must resize its current window");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                first.dispose();
                second.dispose();
            });
        }
    }

    private static void dragEastEdge(JPanel source, JFrame window) {
        int x = source.getWidth() - 1;
        int y = source.getHeight() / 2;
        long now = System.currentTimeMillis();
        source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, now,
                InputEvent.BUTTON1_DOWN_MASK, x, y, window.getX() + x, window.getY() + y,
                1, false, MouseEvent.BUTTON1));
        source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_DRAGGED, now + 1,
                InputEvent.BUTTON1_DOWN_MASK, x + 40, y, window.getX() + x + 40, window.getY() + y,
                0, false, MouseEvent.NOBUTTON));
        source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, now + 2,
                0, x + 40, y, window.getX() + x + 40, window.getY() + y,
                1, false, MouseEvent.BUTTON1));
    }
}
