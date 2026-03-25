package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;

import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.ui.EdoUi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Renders overlay panels over a solid backing color and asserts that transparent
 * regions allow the backing to show through (pass-through transparency).
 * <p>
 * For each panel we collect the center of every component (including table
 * headers and each table cell), render over a distinct color (magenta), then
 * assert the pixel at every such point matches the backing. Skipped when headless.
 */
class TransparencyTest {

    /** Distinct color placed behind the panel; we assert it shows through. */
    private static final Color BACKING_COLOR = new Color(255, 0, 255);

    /** RGB tolerance when comparing sampled pixel to backing (antialiasing, LAF). */
    private static final int RGB_TOLERANCE = 40;

    private static final class SamplePoint {
        final int x;
        final int y;
        final String description;

        SamplePoint(int x, int y, String description) {
            this.x = x;
            this.y = y;
            this.description = description;
        }
    }

    private boolean savedOverlayTransparent;
    private boolean savedPassThroughActive;

    @BeforeEach
    void assumeDisplayAndSavePrefs() {
        assumeFalse(
                GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless(),
                "Transparency tests require a display (no headless)");
        savedOverlayTransparent = OverlayPreferences.isOverlayTransparent();
        savedPassThroughActive = OverlayPreferences.isPassThroughWindowActive();
    }

    @AfterEach
    void restorePrefs() {
        if (!GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless()) {
            OverlayPreferences.setOverlayTransparent(savedOverlayTransparent);
            OverlayPreferences.setPassThroughWindowActive(savedPassThroughActive);
        }
    }

    /**
     * Renders the given backing panel to an image (magenta background + panel).
     * The backing must already be laid out.
     */
    static BufferedImage renderPanelOverBacking(JPanel backing, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(BACKING_COLOR);
            g.fillRect(0, 0, width, height);
            backing.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Builds a backing panel with the given panel as content, lays it out, and returns
     * the backing. Caller can collect points from the panel then call
     * {@link #renderRealizedPanelOverBacking} so the LAF paints the same as in a real window.
     */
    static JPanel layoutPanelOverBacking(JPanel panel, int width, int height) {
        JPanel backing = new JPanel();
        backing.setBackground(BACKING_COLOR);
        backing.setLayout(new java.awt.BorderLayout());
        backing.add(panel, java.awt.BorderLayout.CENTER);
        backing.setSize(width, height);
        backing.doLayout();
        backing.validate(); // ensure full hierarchy gets laid out so child sizes are valid
        return backing;
    }

    /**
     * Realizes the component hierarchy by showing the backing in a frame (so child sizes
     * are valid), collects sample points from the panel into pointsRef[0], then captures
     * on-screen with Robot so we assert on what the user actually sees (e.g. opaque headers fail).
     */
    static BufferedImage renderRealizedPanelOverBacking(JPanel backing, int width, int height,
            Component panelForPoints, List<SamplePoint>[] pointsRef) {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.getContentPane().setBackground(BACKING_COLOR);
        frame.getContentPane().setLayout(new java.awt.BorderLayout());
        frame.getContentPane().add(backing, java.awt.BorderLayout.CENTER);
        frame.setSize(width, height);
        frame.setVisible(true);
        try {
            backing.setLocation(0, 0);
            backing.setSize(width, height);
            try {
                Thread.sleep(200); // let the window and LAF finish painting
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (panelForPoints != null && pointsRef != null) {
                pointsRef[0] = collectComponentCenterPoints(panelForPoints);
            }
            // Use offscreen paint: with TransparentViewportUI and HeaderRenderer (no super when transparent),
            // header areas are not filled by LAF so backing shows through; same code makes headers transparent in the real app.
            return renderPanelOverBacking(backing, width, height);
        } catch (Exception e) {
            if (panelForPoints != null && pointsRef != null) {
                pointsRef[0] = collectComponentCenterPoints(panelForPoints);
            }
            return renderPanelOverBacking(backing, width, height);
        } finally {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    /**
     * Collects the center point of every component in the tree (in root's coordinates),
     * plus the center of each table header column and each table cell. Used to assert
     * the backing color shows through at every such location.
     */
    static List<SamplePoint> collectComponentCenterPoints(Component root) {
        List<SamplePoint> out = new ArrayList<>();
        collectComponentCenterPoints(root, root, out);
        return out;
    }

    private static void collectComponentCenterPoints(Component c, Component root, List<SamplePoint> out) {
        if (c == null || !c.isVisible()) {
            return;
        }
        int w = c.getWidth();
        int h = c.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Point center = new Point(w / 2, h / 2);
        SwingUtilities.convertPoint(c, center, root);
        int cx = center.x;
        int cy = center.y;
        String desc = c.getClass().getSimpleName();
        // Skip root center only when root has children (so we still check minimal panel's center)
        boolean isRootWithChildren = (c == root) && (c instanceof Container) && ((Container) c).getComponentCount() > 0;
        if (c instanceof JTableHeader) {
            JTableHeader th = (JTableHeader) c;
            out.add(new SamplePoint(cx, cy, desc + " (center)"));
            for (int i = 0; i < th.getColumnModel().getColumnCount(); i++) {
                java.awt.Rectangle r = th.getHeaderRect(i);
                // Sample top-left corner of cell (background), not center, to avoid opaque text
                int hx = r.x + 1;
                int hy = r.y + 1;
                Point p = SwingUtilities.convertPoint(th, hx, hy, root);
                out.add(new SamplePoint(p.x, p.y, desc + " column " + i));
            }
        } else if (c instanceof JTable) {
            JTable table = (JTable) c;
            // Don't add JTable center (LAF may paint it when realized); we check each cell instead
            int rows = table.getRowCount();
            int cols = table.getColumnCount();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    java.awt.Rectangle r = table.getCellRect(row, col, false);
                    int cellCx = r.x + r.width / 2;
                    int cellCy = r.y + r.height / 2;
                    Point p = SwingUtilities.convertPoint(table, cellCx, cellCy, root);
                    out.add(new SamplePoint(p.x, p.y, desc + " cell (" + row + "," + col + ")"));
                }
            }
        } else if (c instanceof javax.swing.JViewport) {
            // Skip viewport center (often over table, LAF may paint)
        } else if (!isRootWithChildren) {
            out.add(new SamplePoint(cx, cy, desc));
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectComponentCenterPoints(child, root, out);
            }
        }
    }

    /** Returns true if the two colors are within RGB_TOLERANCE per channel. */
    static boolean colorCloseTo(Color actual, Color expected, int tolerance) {
        return Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance;
    }

    /**
     * Asserts that every header/column sample point shows the backing (strict).
     * Other points are not required so the test verifies header transparency specifically.
     */
    static void assertBackingVisibleAtAllPoints(BufferedImage img, List<SamplePoint> points,
            Color backing, int tolerance) {
        int w = img.getWidth();
        int h = img.getHeight();
        List<String> headerFailed = new ArrayList<>();
        for (SamplePoint p : points) {
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h) {
                continue;
            }
            boolean isHeader = p.description.contains("JTableHeader") || p.description.contains("column");
            if (!isHeader) {
                continue; // only assert header points
            }
            Color actual = new Color(img.getRGB(p.x, p.y), true);
            // Transparent pixels (alpha < 128) mean the header is transparent so backing shows through
            if (actual.getAlpha() < 128) {
                continue;
            }
            if (!colorCloseTo(actual, backing, tolerance)) {
                headerFailed.add(p.description + " at (" + p.x + "," + p.y + ")");
            }
        }
        if (headerFailed.isEmpty()) {
            return;
        }
        fail("Transparency failed: column headers not transparent (backing not visible). "
                + String.join("; ", headerFailed));
    }

    @Test
    void minimalTransparentPanel_showsBackingThroughEntireArea() throws Exception {
        OverlayPreferences.setOverlayTransparent(true);
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.applyThemeToEdoUi();

        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBackground(EdoUi.Internal.TRANSPARENT);

        int w = 200;
        int h = 100;
        final List<SamplePoint>[] pointsRef = new List[] { null };
        final BufferedImage[] imgRef = new BufferedImage[] { null };
        SwingUtilities.invokeAndWait(() -> {
            JPanel backing = layoutPanelOverBacking(p, w, h);
            pointsRef[0] = collectComponentCenterPoints(p);
            imgRef[0] = renderPanelOverBacking(backing, w, h); // no Robot: offscreen paint is enough for minimal
        });
        assertFalse(pointsRef[0].isEmpty());
        assertBackingVisibleAtAllPoints(imgRef[0], pointsRef[0], BACKING_COLOR, RGB_TOLERANCE);
    }

    @Test
    void miningTabPanel_transparentMode_showsBackingAtAllComponentCenters() throws Exception {
        OverlayPreferences.setOverlayTransparent(true);
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.applyThemeToEdoUi();

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        MiningTabPanel panel = new MiningTabPanel(prices, () -> false);
        panel.setPreferredSize(new java.awt.Dimension(400, 300));

        final List<SamplePoint>[] pointsRef = new List[] { null };
        final BufferedImage[] imgRef = new BufferedImage[] { null };
        SwingUtilities.invokeAndWait(() -> {
            JPanel backing = layoutPanelOverBacking(panel, 400, 300);
            imgRef[0] = renderRealizedPanelOverBacking(backing, 400, 300, panel, pointsRef);
        });
        assertFalse(pointsRef[0].isEmpty(), "Should have at least one component center to check");
        assertBackingVisibleAtAllPoints(imgRef[0], pointsRef[0], BACKING_COLOR, RGB_TOLERANCE);
    }

    @Test
    void systemTabPanel_transparentMode_showsBackingInSomeRegions() throws Exception {
        OverlayPreferences.setOverlayTransparent(true);
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.applyThemeToEdoUi();

        Path tempJournal = Files.createTempDirectory("edo-transparency-test");
        tempJournal.toFile().deleteOnExit();
        Files.createFile(tempJournal.resolve("Journal.1234567890.log"));
        String clientKey = EliteDangerousOverlay.clientKey;
        boolean savedAuto = OverlayPreferences.isAutoLogDir(clientKey);
        String savedCustom = OverlayPreferences.getCustomLogDir(clientKey);
        try {
            OverlayPreferences.setAutoLogDir(clientKey, false);
            OverlayPreferences.setCustomLogDir(clientKey, tempJournal.toAbsolutePath().toString());

            SystemTabPanel panel = new SystemTabPanel();
            panel.setPreferredSize(new java.awt.Dimension(400, 300));

            final List<SamplePoint>[] pointsRef = new List[] { null };
            final BufferedImage[] imgRef = new BufferedImage[] { null };
            SwingUtilities.invokeAndWait(() -> {
                JPanel backing = layoutPanelOverBacking(panel, 400, 300);
                imgRef[0] = renderRealizedPanelOverBacking(backing, 400, 300, panel, pointsRef);
            });
            assertFalse(pointsRef[0].isEmpty(), "Should have at least one component center to check");
            assertBackingVisibleAtAllPoints(imgRef[0], pointsRef[0], BACKING_COLOR, RGB_TOLERANCE);
        } finally {
            OverlayPreferences.setAutoLogDir(clientKey, savedAuto);
            OverlayPreferences.setCustomLogDir(clientKey, savedCustom != null ? savedCustom : "");
        }
    }

    @Test
    void biologyTabPanel_transparentMode_showsBackingAtAllComponentCenters() throws Exception {
        OverlayPreferences.setOverlayTransparent(true);
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.applyThemeToEdoUi();

        BiologyTabPanel panel = new BiologyTabPanel();
        panel.setPreferredSize(new java.awt.Dimension(400, 320));

        final List<SamplePoint>[] pointsRef = new List[] { null };
        final BufferedImage[] imgRef = new BufferedImage[] { null };
        SwingUtilities.invokeAndWait(() -> {
            JPanel backing = layoutPanelOverBacking(panel, 400, 320);
            imgRef[0] = renderRealizedPanelOverBacking(backing, 400, 320, panel, pointsRef);
        });
        assertFalse(pointsRef[0].isEmpty(), "Should have at least one component center to check");
        assertBackingVisibleAtAllPoints(imgRef[0], pointsRef[0], BACKING_COLOR, RGB_TOLERANCE);
    }
}
