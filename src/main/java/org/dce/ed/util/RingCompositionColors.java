package org.dce.ed.util;

import java.awt.Color;
import java.util.Locale;

/** Map journal / EDSM ring class strings to map ring colours. */
public final class RingCompositionColors {

    private RingCompositionColors() {
    }

    public static Color fillForRingClass(String ringClass) {
        return fillForRingClass(ringClass, 200);
    }

    public static Color fillForRingClass(String ringClass, int alpha) {
        int a = Math.max(40, Math.min(255, alpha));
        String key = normalize(ringClass);
        if (key.contains("metal")) {
            return new Color(195, 198, 210, a);
        }
        if (key.contains("icy") || key.contains("ice")) {
            return new Color(150, 205, 255, a);
        }
        if (key.contains("rocky") || key.contains("rock")) {
            return new Color(175, 135, 95, a);
        }
        return new Color(170, 170, 180, a);
    }

    public static Color strokeForRingClass(String ringClass) {
        Color fill = fillForRingClass(ringClass, 255);
        return new Color(
                Math.min(255, fill.getRed() + 25),
                Math.min(255, fill.getGreen() + 25),
                Math.min(255, fill.getBlue() + 25),
                255);
    }

    private static String normalize(String ringClass) {
        if (ringClass == null || ringClass.isBlank()) {
            return "";
        }
        return ringClass.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
