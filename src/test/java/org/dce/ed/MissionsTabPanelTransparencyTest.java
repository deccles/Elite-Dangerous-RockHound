package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class MissionsTabPanelTransparencyTest {

    @Test
    void normalMouseMode_paintsSolidBackingBelowTransportRows() throws Exception {
        assertEquals(255, renderUnusedTableAreaAlpha(MouseInteractionMode.NORMAL));
    }

    @Test
    void selectiveMouseMode_keepsUnusedTransportAreaTransparent() throws Exception {
        assertEquals(0, renderUnusedTableAreaAlpha(MouseInteractionMode.SELECTIVE));
    }

    private static int renderUnusedTableAreaAlpha(MouseInteractionMode mode) throws Exception {
        AtomicInteger alpha = new AtomicInteger(-1);
        SwingUtilities.invokeAndWait(() -> {
            MissionsTabPanel panel = new MissionsTabPanel(() -> false, () -> false, () -> "Sol", () -> null);
            JRootPane root = new JRootPane();
            root.getContentPane().setLayout(new BorderLayout());
            root.getContentPane().add(panel, BorderLayout.CENTER);
            OverlayPreferences.publishWindowChromeTransparency(root, true, 100);
            root.putClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY, mode);

            root.setSize(640, 480);
            layoutTree(root);

            JScrollPane scroll = findDescendant(panel, JScrollPane.class);
            assertTrue(scroll != null, "Transport missions scroll pane must exist");
            JViewport viewport = scroll.getViewport();
            assertTrue(viewport.getWidth() > 20 && viewport.getHeight() > 20,
                    "Transport viewport must have a paintable unused area");
            Point sample = SwingUtilities.convertPoint(
                    viewport, viewport.getWidth() / 2, viewport.getHeight() / 2, panel);

            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                panel.paint(g);
            } finally {
                g.dispose();
            }
            alpha.set((image.getRGB(sample.x, sample.y) >>> 24) & 0xFF);
        });
        return alpha.get();
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                layoutTree(container);
            }
        }
    }

    private static <T extends Component> T findDescendant(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findDescendant(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
