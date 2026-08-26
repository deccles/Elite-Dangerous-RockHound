package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class StatusCircleIconTest {

    @Test
    void emptyCircleUsesVisibleStatusColorForItsRim() {
        Color rim = new Color(120, 140, 160);
        StatusCircleIcon icon = new StatusCircleIcon(rim, "", false, 0.0f);
        BufferedImage image = paint(icon);
        Color topCenter = new Color(image.getRGB(icon.getIconWidth() / 2, 0), true);

        assertTrue(topCenter.getAlpha() > 0);
        assertTrue(topCenter.getBlue() > topCenter.getRed());
    }

    @Test
    void glowDoesNotTintTheSymbol() {
        StatusCircleIcon icon = new StatusCircleIcon(new Color(120, 140, 160), "?", true, 0.42f);
        BufferedImage image = paint(icon);
        boolean hasBlackSymbolPixel = false;
        for (int y = 3; y < icon.getIconHeight() - 3; y++) {
            for (int x = 3; x < icon.getIconWidth() - 3; x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 200 && pixel.getRed() < 20
                        && pixel.getGreen() < 20 && pixel.getBlue() < 20) {
                    hasBlackSymbolPixel = true;
                }
            }
        }

        assertTrue(hasBlackSymbolPixel);
    }

    @Test
    void glowBrightensTheCircleTowardWhite() {
        StatusCircleIcon icon = new StatusCircleIcon(new Color(30, 100, 180), "", false, 1.0f);
        BufferedImage image = paint(icon);
        boolean hasWhiteRimPixel = false;
        for (int y = 0; y < icon.getIconHeight(); y++) {
            for (int x = 0; x < icon.getIconWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 180 && pixel.getRed() > 220
                        && pixel.getGreen() > 220 && pixel.getBlue() > 220) {
                    hasWhiteRimPixel = true;
                }
            }
        }

        assertTrue(hasWhiteRimPixel);
    }

    private static BufferedImage paint(StatusCircleIcon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
