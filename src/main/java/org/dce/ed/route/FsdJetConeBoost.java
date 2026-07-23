package org.dce.ed.route;

/**
 * FSD jet-cone (supercharge) multipliers from remnant star classes on a plotted route.
 * <p>
 * Charging in a neutron jet multiplies the <em>next</em> hyperspace jump's range by 4;
 * a white dwarf multiplies by 1.5. The galaxy map's "use jet-cone boost" plotter assumes
 * the commander charges before leaving each remnant, so fuel prediction scales the
 * departure hop the same way.
 */
public final class FsdJetConeBoost {

    /** Neutron star jet-cone range multiplier. */
    public static final double NEUTRON = 4.0;
    /** White dwarf jet-cone range multiplier. */
    public static final double WHITE_DWARF = 1.5;

    private FsdJetConeBoost() {
    }

    /**
     * Range multiplier for the hyperspace jump <em>leaving</em> this star class (1.0 if none).
     * NavRoute uses {@code N} for neutrons and {@code D}/{@code DA}/… for white dwarfs.
     */
    public static double multiplierLeaving(String starClass) {
        if (starClass == null) {
            return 1.0;
        }
        String s = starClass.trim();
        if (s.isEmpty()) {
            return 1.0;
        }
        char c = Character.toUpperCase(s.charAt(0));
        if (c == 'N') {
            return NEUTRON;
        }
        if (c == 'D') {
            return WHITE_DWARF;
        }
        return 1.0;
    }

    public static boolean isNeutron(String starClass) {
        return multiplierLeaving(starClass) == NEUTRON;
    }

    public static boolean isWhiteDwarf(String starClass) {
        return multiplierLeaving(starClass) == WHITE_DWARF;
    }
}
