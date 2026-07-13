package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.border.Border;

/**
 * Debug helper: walk a Swing subtree and report components whose background reads as white
 * (common cause of stray frames on transparent overlay tabs).
 */
public final class OverlayComponentColorAnalyzer {

    public record Finding(
            String path,
            String className,
            String name,
            boolean opaque,
            Color background,
            String border) {
    }

    private static final int WHITE_THRESHOLD = 240;

    private OverlayComponentColorAnalyzer() {
    }

    public static boolean isWhiteOrNearWhite(Color color) {
        if (color == null) {
            return false;
        }
        if (color.getAlpha() == 0) {
            return false;
        }
        return color.getRed() >= WHITE_THRESHOLD
                && color.getGreen() >= WHITE_THRESHOLD
                && color.getBlue() >= WHITE_THRESHOLD;
    }

    public static List<Finding> analyzeWhiteComponents(Component root) {
        List<Finding> out = new ArrayList<>();
        if (root != null) {
            walk(root, "", out);
        }
        return out;
    }

    public static void logWhiteComponents(Component root, String tag) {
        List<Finding> findings = analyzeWhiteComponents(root);
        System.out.println("[EDO][ComponentColors][" + tag + "] white/near-white components: " + findings.size());
        for (Finding f : findings) {
            System.out.printf(Locale.US,
                    "  %s | %s name=%s opaque=%s bg=#%08X border=%s%n",
                    f.path(),
                    f.className(),
                    f.name(),
                    f.opaque(),
                    f.background() != null ? f.background().getRGB() : 0,
                    f.border());
        }
    }

    private static void walk(Component component, String path, List<Finding> out) {
        String here = path.isEmpty() ? component.getClass().getSimpleName() : path + " > " + component.getClass().getSimpleName();
        if (component instanceof JComponent jc) {
            Color bg = jc.getBackground();
            if (jc.isOpaque() && isWhiteOrNearWhite(bg)) {
                Border border = jc.getBorder();
                out.add(new Finding(
                        here,
                        jc.getClass().getName(),
                        jc.getName(),
                        jc.isOpaque(),
                        bg,
                        border != null ? border.getClass().getSimpleName() : "null"));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                walk(child, here, out);
            }
        }
    }
}
