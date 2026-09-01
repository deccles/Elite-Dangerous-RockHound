package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JButton;

import org.junit.jupiter.api.Test;

class OverlayOutlineButtonStyleTest {

    @Test
    void disabledHitSafePrimaryMatchesKillScriptsIdleGrayWhenThemeInkIsNotForced() {
        Font font = new Font("Dialog", Font.BOLD, 16);

        JButton disabled = new JButton("Target Selected");
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(disabled, font, false);
        disabled.setEnabled(false);

        JButton killIdle = new JButton("Kill scripts");
        OverlayOutlineButtonStyle.applyDangerHitSafe(killIdle, font, false);

        Color disabledInk = brightestNonPlate(paint(disabled), disabled.getBackground());
        Color killInk = brightestNonPlate(paint(killIdle), killIdle.getBackground());

        assertIdleGray(disabledInk, "disabled tab Exec button");
        assertIdleGray(killInk, "Kill scripts idle");
        assertTrue(Math.abs(luminance(disabledInk) - luminance(killInk)) < 25,
                "disabled ink " + disabledInk + " should match Kill scripts idle " + killInk);
    }

    @Test
    void disabledChipUsesIdleGrayInsteadOfMuddyOrange() {
        Font font = new Font("Dialog", Font.BOLD, 16);
        JButton chip = new JButton("Chip");
        OverlayOutlineButtonStyle.applyChip(chip, font, true);
        chip.setEnabled(false);

        Color ink = brightestNonPlate(paint(chip), EdoUi.User.BACKGROUND);
        assertIdleGray(ink, "disabled chip");
    }

    private static void assertIdleGray(Color ink, String label) {
        assertTrue(luminance(ink) >= 100, label + " text is too dark: " + ink);
        assertTrue(chroma(ink) <= 40, label + " text should be gray, not orange: " + ink);
    }

    private static BufferedImage paint(JButton button) {
        Dimension pref = button.getPreferredSize();
        int width = Math.max(180, pref.width);
        int height = Math.max(36, pref.height);
        button.setSize(width, height);
        button.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            button.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Color brightestNonPlate(BufferedImage image, Color plate) {
        Color brightest = plate;
        int best = luminance(plate);
        int plateRgb = plate.getRGB();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (rgb == plateRgb) {
                    continue;
                }
                Color pixel = new Color(rgb, true);
                if (pixel.getAlpha() < 200) {
                    continue;
                }
                int lum = luminance(pixel);
                if (lum > best) {
                    best = lum;
                    brightest = pixel;
                }
            }
        }
        return brightest;
    }

    private static int luminance(Color color) {
        return (color.getRed() * 3 + color.getGreen() * 4 + color.getBlue()) / 8;
    }

    private static int chroma(Color color) {
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        return max - min;
    }
}
