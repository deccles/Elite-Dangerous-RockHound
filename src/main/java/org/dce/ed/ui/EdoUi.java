package org.dce.ed.ui;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EdoUi {

    private EdoUi() {
    }

    /**
     * User-configurable theme colors. These are the colors that should be exposed in a preferences UI.
     * Keep this list small (roughly 10-15).
     */
    public static final class User {
        private User() {
        }

        // Core theme
        public static Color MAIN_TEXT = new Color(255, 140, 0); // ED orange
        public static Color BACKGROUND = new Color(10, 10, 10);
        public static Color PANEL_BG = new Color(22, 22, 22);

        // Semantic/status
        public static Color SUCCESS = new Color(0, 200, 0);
        public static Color WARNING = new Color(255, 215, 0);
        public static Color ERROR = new Color(200, 40, 40);

        // Special/meaningful
        public static Color VALUABLE = new Color(212, 175, 55); // gold
        /** Mining tab: color for discovered core (motherlode) materials. */
        public static Color CORE_BLUE = new Color(100, 180, 255);

        /** System tab: landable sneaker icon — canvas / upper (preferences). */
        public static Color SNEAKER = new Color(206, 44, 44);

        /** Completed exobiology (3/3); prospector row matching configured high-value material filters (preferences). */
        public static Color PRIMARY_HIGHLIGHT = new Color(0, 200, 0);

        /** Exobiology in progress (1–2 samples) on System and Exobiology tabs (preferences). */
        public static Color SECONDARY_HIGHLIGHT = new Color(255, 255, 0);
    }

    /**
     * Internal, non-configurable colors (and derived variants). Centralize everything here so
     * no other classes need to create new Color(...) directly.
     */
    public static final class Internal {
        private Internal() {
        }

        public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

        // Common alpha variants derived from MAIN_TEXT
        public static Color mainTextAlpha(int alpha) {
            return withAlpha(User.MAIN_TEXT, alpha);
        }

        // Separator lines should track the main font color.
        public static Color separatorLine() {
            return mainTextAlpha(64);
        }

        public static Color separatorLineStrong() {
            return mainTextAlpha(96);
        }

        // Table header styling
        public static Color tableHeaderForeground() {
            return User.MAIN_TEXT;
        }

        public static Color tableHeaderTopBorder() {
            return separatorLineStrong();
        }

        // Neutral overlays
        public static Color blackAlpha(int alpha) {
            return withAlpha(Color.BLACK, alpha);
        }

        public static Color whiteAlpha(int alpha) {
            return withAlpha(Color.WHITE, alpha);
        }

        // Specific project colors that aren't worth user-configuring (kept centralized)
        public static final Color LOG_LIGHT_ORANGE = new Color(255, 235, 200);

        public static final Color BROWN_DARK = new Color(140, 110, 25);
        public static final Color BROWN_DARKER = new Color(50, 35, 10);

        public static final Color GRAY_120 = new Color(120, 120, 120);
        public static final Color GRAY_180 = new Color(180, 180, 180);

        public static final Color GRAY_ALPHA_140 = withAlpha(GRAY_180, 140);
        public static final Color GRAY_ALPHA_200 = withAlpha(GRAY_180, 200);

        public static Color MAIN_TEXT_ALPHA_40 = mainTextAlpha(40);
        public static Color MAIN_TEXT_ALPHA_140 = mainTextAlpha(140);
        public static Color MAIN_TEXT_ALPHA_180 = mainTextAlpha(180);
        public static Color MAIN_TEXT_ALPHA_200 = mainTextAlpha(200);
        public static Color MAIN_TEXT_ALPHA_220 = mainTextAlpha(220);

        public static final Color WHITE_ALPHA_64 = whiteAlpha(64);
        public static final Color WHITE_ALPHA_200 = whiteAlpha(200);
        public static final Color WHITE_ALPHA_230 = whiteAlpha(230);

        public static final Color BLACK_ALPHA_80 = blackAlpha(80);
        public static final Color BLACK_ALPHA_140 = blackAlpha(140);
        public static final Color BLACK_ALPHA_180 = blackAlpha(180);

        // Menu/popup surfaces (kept internal; if you later want prefs, map them to User.PANEL_BG)
        public static final Color DARK_14 = new Color(14, 14, 14);
        public static final Color DARK_22 = new Color(22, 22, 22);
        public static final Color MENU_FG_LIGHT = new Color(230, 230, 230);
        public static Color MENU_ACCENT = User.MAIN_TEXT;// Title bar / close button
        public static final Color TITLEBAR_BG = withAlpha(new Color(32, 32, 32), 230);
        public static final Color TITLEBAR_BG_HOVER = withAlpha(new Color(60, 60, 60), 230);
        public static final Color TITLEBAR_BG_ACTIVE = withAlpha(new Color(90, 90, 90), 230);
        public static final Color CLOSE_BG = withAlpha(new Color(200, 40, 40), 230);
        public static final Color CLOSE_BG_HOVER = withAlpha(new Color(150, 20, 20), 230);

        // Overlay/tab backgrounds
        public static final Color DARK_ALPHA_220 = withAlpha(new Color(50, 50, 50), 220);
    }

    // ---- Alpha helper + cache ----

    private static final ConcurrentMap<Long, Color> ALPHA_CACHE = new ConcurrentHashMap<>();

    public static Color withAlpha(Color base, int alpha) {
        if (base == null) {
            return null;
        }

        final int a;
        if (alpha < 0) {
            a = 0;
        } else if (alpha > 255) {
            a = 255;
        } else {
            a = alpha;
        }

        if (a == 255) {
            return base;
        }

        long key = (((long) base.getRGB()) << 8) ^ (long) a;

        return ALPHA_CACHE.computeIfAbsent(key,
                k -> new Color(base.getRed(), base.getGreen(), base.getBlue(), a));
    }

    
    // ---- Simple factory helpers (keep Color construction centralized) ----

    public static Color rgb(int r, int g, int b) {
        return new Color(r, g, b);
    }

    public static Color rgba(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    public static Color fromRgbInt(int rgb) {
        return new Color(rgb);
    }

// ---- Backwards-compatible aliases (so call-sites can remain unchanged for now) ----

    public static final Color ED_DARK = Color.BLACK;
    public static final Color TEXT_BLACK = Color.BLACK;

 // ---- Backwards-compatible aliases (so call-sites can remain unchanged for now) ----

    public static final Color STATUS_GRAY  = new Color(180, 180, 180);;
    public static Color STATUS_BLUE = User.MAIN_TEXT;   // no blue: map to main text color
    public static final Color STATUS_YELLOW = User.WARNING;

    public static Color ED_ORANGE_TRANS = withAlpha(User.MAIN_TEXT, 64);
    public static Color ED_ORANGE_LESS_TRANS = withAlpha(User.MAIN_TEXT, 96);

    // ---- Theme refresh ----

    /**
     * Recompute any cached/derived theme colors after changing {@link User#MAIN_TEXT} or {@link User#BACKGROUND}.
     * Call this after loading theme prefs, or after saving theme prefs from the Preferences dialog.
     */
    public static void refreshDerivedColors() {
        // MAIN_TEXT-derived cached variants
        Internal.MAIN_TEXT_ALPHA_40 = Internal.mainTextAlpha(40);
        Internal.MAIN_TEXT_ALPHA_140 = Internal.mainTextAlpha(140);
        Internal.MAIN_TEXT_ALPHA_180 = Internal.mainTextAlpha(180);
        Internal.MAIN_TEXT_ALPHA_200 = Internal.mainTextAlpha(200);
        Internal.MAIN_TEXT_ALPHA_220 = Internal.mainTextAlpha(220);

        // Backwards-compat aliases
        STATUS_BLUE = User.MAIN_TEXT;
        ED_ORANGE_TRANS = withAlpha(User.MAIN_TEXT, 64);
        ED_ORANGE_LESS_TRANS = withAlpha(User.MAIN_TEXT, 96);

        // Internal accents that should track the main text color
        Internal.MENU_ACCENT = User.MAIN_TEXT;

        // Changing the background doesn't require cached recompute today, but keep this method centralized
        // so it's obvious where to add any background-derived cached colors in the future.
    }

}