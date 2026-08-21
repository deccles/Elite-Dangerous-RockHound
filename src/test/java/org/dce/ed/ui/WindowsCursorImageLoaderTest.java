package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class WindowsCursorImageLoaderTest {

    @Test
    void loadsInstalledStandardArrowWithItsHotspot() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));

        WindowsCursorImageLoader.CursorImage cursor = WindowsCursorImageLoader.loadStandardArrow();

        assertNotNull(cursor);
        assertNotNull(cursor.image());
        assertTrue(cursor.image().getWidth() >= 24);
        assertTrue(cursor.image().getHeight() >= 24);
        assertEquals(0, cursor.hotspot().x);
        assertEquals(0, cursor.hotspot().y);
        int visiblePixels = 0;
        for (int y = 0; y < cursor.image().getHeight(); y++) {
            for (int x = 0; x < cursor.image().getWidth(); x++) {
                if ((cursor.image().getRGB(x, y) >>> 24) != 0) {
                    visiblePixels++;
                }
            }
        }
        assertTrue(visiblePixels > 50, "decoded arrow must contain visible cursor pixels");
    }
}
