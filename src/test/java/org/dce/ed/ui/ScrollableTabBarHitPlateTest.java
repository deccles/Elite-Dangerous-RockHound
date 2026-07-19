package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.dce.ed.OverlayPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tab bar must stay visually transparent in interactive (mouse pass-through OFF) overlay mode,
 * while never painting alpha-0 pixels there — Windows layered hit-testing drops clicks on alpha-0
 * pixels, so tabs/gaps would become unclickable.
 */
class ScrollableTabBarHitPlateTest {

    private boolean savedPassThroughActive;
    private boolean savedMousePassThrough;
    private int savedNormalPct;
    private int savedPassThroughPct;

    @BeforeEach
    void savePrefs() {
        savedPassThroughActive = OverlayPreferences.isPassThroughWindowActive();
        savedMousePassThrough = OverlayPreferences.isOverlayMousePassThroughToGame();
        savedNormalPct = OverlayPreferences.getNormalTransparencyPercent();
        savedPassThroughPct = OverlayPreferences.getPassThroughTransparencyPercent();
    }

    @AfterEach
    void restorePrefs() {
        OverlayPreferences.setPassThroughWindowActive(savedPassThroughActive);
        OverlayPreferences.setOverlayMousePassThroughToGame(savedMousePassThrough);
        OverlayPreferences.setNormalTransparencyPercent(savedNormalPct);
        OverlayPreferences.setPassThroughTransparencyPercent(savedPassThroughPct);
    }

    private static BufferedImage paintBar(ScrollableTabBar bar, int w, int h) {
        bar.setSize(w, h);
        bar.doLayout();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            bar.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    @Test
    void interactiveSeeThrough_paintsMinAlphaHitPlate_notOpaque() {
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.setOverlayMousePassThroughToGame(false);
        OverlayPreferences.setNormalTransparencyPercent(100);

        ScrollableTabBar bar = new ScrollableTabBar(() -> false, false);
        bar.applyOverlayChrome(new Color(0, 0, 0, 0), true);

        BufferedImage img = paintBar(bar, 120, 24);
        int alpha = new Color(img.getRGB(60, 12), true).getAlpha();
        assertTrue(alpha >= 1, "pixels must stay hit-testable (alpha >= 1), was " + alpha);
        assertTrue(alpha <= 8, "bar must stay visually transparent (near-zero alpha), was " + alpha);
    }

    @Test
    void interactiveSeeThrough_keepsConfiguredTransparencyAlpha() {
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.setOverlayMousePassThroughToGame(false);
        OverlayPreferences.setNormalTransparencyPercent(93);

        // 93% transparent => alpha ~18, matching OverlayFrame.applyOverlayBackgroundPreview.
        int expectedAlpha = (int) Math.round(255.0 * (1.0 - 0.93));
        ScrollableTabBar bar = new ScrollableTabBar(() -> false, false);
        bar.applyOverlayChrome(new Color(10, 10, 10, expectedAlpha), true);

        BufferedImage img = paintBar(bar, 120, 24);
        int alpha = new Color(img.getRGB(60, 12), true).getAlpha();
        assertEquals(expectedAlpha, alpha, "bar must follow the Normal transparency percent");
    }

    @Test
    void mousePassThrough_clearsToFullyTransparent() {
        OverlayPreferences.setPassThroughWindowActive(true);
        OverlayPreferences.setOverlayMousePassThroughToGame(true);
        OverlayPreferences.setPassThroughTransparencyPercent(100);

        ScrollableTabBar bar = new ScrollableTabBar(() -> true, false);
        bar.applyOverlayChrome(new Color(0, 0, 0, 0), true);

        BufferedImage img = paintBar(bar, 120, 24);
        int alpha = new Color(img.getRGB(60, 12), true).getAlpha();
        assertEquals(0, alpha, "pass-through mode clears the bar completely");
    }

    @Test
    void minAlphaHitPlate_clampsAlphaToAtLeastOne() {
        assertEquals(1, ScrollableTabBar.minAlphaHitPlate(new Color(5, 6, 7, 0)).getAlpha());
        assertEquals(18, ScrollableTabBar.minAlphaHitPlate(new Color(5, 6, 7, 18)).getAlpha());
    }
}
