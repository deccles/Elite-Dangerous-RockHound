package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

class CircularArrowIconTest {

    @Test
    void loopGlyphHasModerateRingAndProminentLeftArrowhead() {
        BufferedImage image = render(new CircularArrowIcon(16));

        assertTrue(alphaAt(image, 4, 7) >= 128,
                "center of the prominent left arrowhead should be filled");
        assertTrue(alphaAt(image, 6, 4) >= 128,
                "wider arrowhead should fill its expanded upper edge");
        assertTrue(alphaAt(image, 0, 6) < 128,
                "right-shifted arrowhead should clear the old left edge");
        assertTrue(alphaAt(image, 8, 4) < 128,
                "inside edge of the ring should remain open instead of looking filled-in");
        assertTrue(alphaAt(image, 13, 8) >= 128,
                "right edge of the thin ring should remain clearly visible");
        assertTrue(alphaAt(image, 12, 8) < 128,
                "inside edge of the ring should stay thin");
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xFF;
    }

    private static BufferedImage render(CircularArrowIcon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(new JToggleButton(), graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
