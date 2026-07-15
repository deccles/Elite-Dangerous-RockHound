package org.dce.ed.util;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public final class AppIconUtil {

    /**
     * Window + taskbar icon (classpath). Zoomed-in branding for small chrome; splash uses
     * {@link #SPLASH_ICON_RESOURCE}.
     */
    public static final String APP_ICON_RESOURCE = "/org/dce/ed/RockHound-window.png";

    /**
     * Wider framing for startup splash only (not used for window / taskbar icons).
     * Original splash art remains at {@code /org/dce/ed/RockHound.png}.
     */
    private static final String SPLASH_ICON_RESOURCE = "/org/dce/ed/RockHound-splash-rings.png";

    /** Previous splash artwork (wolf howl without ringed planet); kept as fallback. */
    private static final String LEGACY_SPLASH_ICON_RESOURCE = "/org/dce/ed/RockHound.png";

    private static final String LEGACY_APP_ICON_RESOURCE = "/org/dce/ed/edsm/locate_icon.png";

    /**
     * Icon sizes for window / taskbar (px, max side). Largest first so {@link Window#getIconImage()} and peers
     * default to a sharp size for the title bar; smaller entries cover tray and low-DPI contexts.
     */
    private static final int[] ICON_FAMILY_MAX_SIDES_DESC = {
            512, 256, 128, 96, 64, 48, 40, 32, 24, 20, 16
    };

    private AppIconUtil() {
        // utility
    }

    /**
     * Loads splash artwork (wider framing than the window icon), trimmed for transparency.
     *
     * @return non-null image, or {@code null} if no resource is available
     */
    public static BufferedImage loadAppIconForSplash() {
        BufferedImage src = loadIconBuffered(SPLASH_ICON_RESOURCE);
        if (src == null) {
            src = loadIconBuffered(LEGACY_SPLASH_ICON_RESOURCE);
        }
        if (src == null) {
            src = loadIconBuffered(APP_ICON_RESOURCE);
        }
        if (src == null) {
            src = loadIconBuffered(LEGACY_APP_ICON_RESOURCE);
        }
        if (src == null) {
            return null;
        }
        return prepareBrandingImage(src);
    }

    /**
     * Same artwork pipeline as {@link #applyAppIcon(Window, String)} for {@link #APP_ICON_RESOURCE}
     * (zoomed window/taskbar branding), for UI that cannot call {@code applyAppIcon} (e.g. title-bar JLabel).
     */
    public static BufferedImage loadPreparedWindowIcon() {
        BufferedImage src = loadIconBuffered(APP_ICON_RESOURCE);
        if (src == null) {
            src = loadIconBuffered(LEGACY_APP_ICON_RESOURCE);
        }
        if (src == null) {
            return null;
        }
        return prepareBrandingImage(src);
    }

    /**
     * Sets both the JFrame/window icon and the OS taskbar icon (when supported).
     *
     * @param window any top-level window (JFrame, JDialog, etc.)
     * @param resourcePath classpath resource path, e.g. {@link #APP_ICON_RESOURCE}
     */
    public static void applyAppIcon(Window window, String resourcePath) {
        BufferedImage src = loadIconBuffered(resourcePath);
        if (src == null && APP_ICON_RESOURCE.equals(resourcePath)) {
            src = loadIconBuffered(LEGACY_APP_ICON_RESOURCE);
        }
        if (src == null) {
            return;
        }

        src = prepareBrandingImage(src);

        List<Image> family = buildIconFamily(src);
        if (family.isEmpty()) {
            return;
        }

        // Multiple resolutions: first image is the default (see Window#setIconImages) — use largest for title bar.
        window.setIconImages(family);

        Image taskbarPreferred = family.get(0);
        if (Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(taskbarPreferred);
            }
        }

        // Windows: Taskbar.Feature.ICON_IMAGE is unsupported; WM_SETICON supplies taskbar / Alt-Tab artwork.
        WindowsWindowIcons.refreshAfterAppIcon(window, src);
    }

    /**
     * Trims fully transparent margins when the source has alpha. Does not recolor pixels: black strokes in the PNG
     * stay opaque (a prior near-black knockout made taskbar icons lose intentional line art on dark backgrounds).
     */
    private static BufferedImage prepareBrandingImage(BufferedImage src) {
        if (src == null) {
            return null;
        }
        BufferedImage trimmed = trimTransparentMargins(src, 20);
        // RockHound-window.png ships with an opaque light-gray "matte" filling the square behind the round logo;
        // trim does nothing until those pixels are cleared.
        floodRemoveLightMatteFromCorners(trimmed);
        return trimTransparentMargins(trimmed, 20);
    }

    /**
     * Removes a connected light-gray/white region touching any image corner (common bad export: fake "transparency"
     * as RGB matte). Stops at saturated logo colors. Aborts without changes if the region would cover most of the
     * image (unusual assets).
     */
    private static void floodRemoveLightMatteFromCorners(BufferedImage img) {
        if (img == null || img.getType() != BufferedImage.TYPE_INT_ARGB) {
            return;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < 2 || h < 2) {
            return;
        }
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[][] corners = {{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}};
        for (int[] c : corners) {
            int x = c[0];
            int y = c[1];
            if (!seen[x][y] && isLightMatteForFlood(img.getRGB(x, y))) {
                seen[x][y] = true;
                q.add(c);
            }
        }
        if (q.isEmpty()) {
            return;
        }
        int maxCells = Math.max(256, (w * h * 9) / 20);
        ArrayList<int[]> cleared = new ArrayList<>();
        while (!q.isEmpty()) {
            int[] p = q.removeFirst();
            cleared.add(p);
            if (cleared.size() > maxCells) {
                return;
            }
            int x = p[0];
            int y = p[1];
            if (x > 0) {
                tryEnqueue(img, seen, q, x - 1, y);
            }
            if (x + 1 < w) {
                tryEnqueue(img, seen, q, x + 1, y);
            }
            if (y > 0) {
                tryEnqueue(img, seen, q, x, y - 1);
            }
            if (y + 1 < h) {
                tryEnqueue(img, seen, q, x, y + 1);
            }
        }
        for (int i = 0; i < cleared.size(); i++) {
            int[] p = cleared.get(i);
            img.setRGB(p[0], p[1], 0);
        }
    }

    private static void tryEnqueue(BufferedImage img, boolean[][] seen, ArrayDeque<int[]> q, int x, int y) {
        if (seen[x][y]) {
            return;
        }
        if (!isLightMatteForFlood(img.getRGB(x, y))) {
            return;
        }
        seen[x][y] = true;
        q.addLast(new int[] {x, y});
    }

    /** Pixels that belong to the flat studio background; not passed through for low-alpha edge blends. */
    private static boolean isLightMatteForFlood(int argb) {
        int a = (argb >>> 24) & 0xff;
        if (a < 170) {
            return false;
        }
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        return r >= 198 && g >= 198 && b >= 198;
    }

    private static void drawImageScaledArgbSrc(Graphics2D g2, BufferedImage src, int dx, int dy, int dw, int dh) {
        Composite prev = g2.getComposite();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(src, dx, dy, dw, dh, null);
        } finally {
            g2.setComposite(prev);
        }
    }

    private static void normalizeFullyTransparentPixels(BufferedImage img) {
        if (img == null) {
            return;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xff) == 0) {
                    img.setRGB(x, y, 0);
                }
            }
        }
    }

    /**
     * Tight-crop PNGs that use transparency outside the visible icon so taskbar/title-bar scaling uses more of the pixel budget.
     */
    private static BufferedImage trimTransparentMargins(BufferedImage src, int alphaThreshold) {
        if (src == null || !src.getColorModel().hasAlpha()) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((src.getRGB(x, y) >>> 24) & 0xff) > alphaThreshold) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX) {
            return src;
        }
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) {
            return src;
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        BufferedImage sub = src.getSubimage(minX, minY, cw, ch);
        BufferedImage copy = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            drawImageScaledArgbSrc(g2, sub, 0, 0, cw, ch);
        } finally {
            g2.dispose();
        }
        return copy;
    }

    private static List<Image> buildIconFamily(BufferedImage src) {
        List<Image> out = new ArrayList<>(ICON_FAMILY_MAX_SIDES_DESC.length);
        for (int maxSide : ICON_FAMILY_MAX_SIDES_DESC) {
            out.add(scaleToMaxSide(src, maxSide));
        }
        return out;
    }

    private static BufferedImage scaleToMaxSide(BufferedImage src, int maxSide) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0 || maxSide <= 0) {
            return src;
        }
        double scale = Math.min((double) maxSide / sw, (double) maxSide / sh);
        int tw = Math.max(1, (int) Math.round(sw * scale));
        int th = Math.max(1, (int) Math.round(sh * scale));
        if (tw == sw && th == sh) {
            return src;
        }
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(tw, th, type);
        Graphics2D g2 = dst.createGraphics();
        try {
            drawImageScaledArgbSrc(g2, src, 0, 0, tw, th);
        } finally {
            g2.dispose();
        }
        if (type == BufferedImage.TYPE_INT_ARGB) {
            normalizeFullyTransparentPixels(dst);
        }
        return dst;
    }

    private static BufferedImage loadIconBuffered(String resourcePath) {
        try (InputStream in = AppIconUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return null;
            }
            return ensureIntArgb(img);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * PNGs may decode to types without a usable alpha raster; normalize so scaling and Windows DIB upload keep
     * transparent corners.
     */
    private static BufferedImage ensureIntArgb(BufferedImage img) {
        if (img == null) {
            return null;
        }
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return img;
        }
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(img, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return out;
    }
}
