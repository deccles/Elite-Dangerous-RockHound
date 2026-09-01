package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

class SegmentedProgressBarTest {

    @Test
    void paintsOneSeparatedHorizontalLinePerModule() {
        SegmentedProgressBar bar = new SegmentedProgressBar();
        bar.setSize(100, 24);
        bar.setProgress(List.of(1.0, 0.5, 0.0), false);

        BufferedImage image = new BufferedImage(100, 24, BufferedImage.TYPE_INT_ARGB);
        bar.paint(image.getGraphics());

        assertEquals(0, image.getRGB(10, 2) >>> 24,
                "shorter bar should remain vertically centered");
        assertEquals(Color.GREEN.getRGB(), image.getRGB(10, 7));
        assertEquals(Color.YELLOW.getRGB(), image.getRGB(10, 11));
        assertEquals(0, (image.getRGB(10, 9) >>> 24), "gap between lines must be transparent");
    }

    @Test
    void singleBar_doesNotExceedThreeEighthsRowHeight() {
        SegmentedProgressBar bar = new SegmentedProgressBar();
        bar.setSize(100, 40);
        bar.setProgress(List.of(1.0), false);

        BufferedImage image = new BufferedImage(100, 40, BufferedImage.TYPE_INT_ARGB);
        bar.paint(image.getGraphics());

        assertEquals(0, image.getRGB(10, 8) >>> 24, "top of a tall row should stay empty");
        assertEquals(0, image.getRGB(10, 31) >>> 24, "bottom of a tall row should stay empty");
        assertEquals(Color.GREEN.getRGB(), image.getRGB(10, 20));
    }

    @Test
    void paintsIncompleteLinesRedWhenMaterialsAreShort() {
        SegmentedProgressBar bar = new SegmentedProgressBar();
        bar.setSize(100, 16);
        bar.setProgress(List.of(1.0, 0.5), true);

        BufferedImage image = new BufferedImage(100, 16, BufferedImage.TYPE_INT_ARGB);
        bar.paint(image.getGraphics());

        assertEquals(Color.GREEN.getRGB(), image.getRGB(10, 6));
        assertEquals(Color.RED.getRGB(), image.getRGB(10, 9));
    }
}
